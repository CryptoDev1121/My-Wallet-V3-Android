package piuk.blockchain.android.coincore.impl.txEngine.swap

import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.TransferLimits
import com.blockchain.swap.nabu.datamanagers.TransferDirection
import com.blockchain.swap.nabu.datamanagers.CustodialOrder
import com.blockchain.swap.nabu.datamanagers.repositories.QuotesProvider
import com.blockchain.swap.nabu.models.nabu.KycTierLevel
import com.blockchain.swap.nabu.models.nabu.KycTiers
import com.blockchain.swap.nabu.service.TierService
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.ExchangeRate
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.PendingTx
import piuk.blockchain.android.coincore.TxConfirmationValue
import piuk.blockchain.android.coincore.TxValidationFailure
import piuk.blockchain.android.coincore.ValidationState
import piuk.blockchain.android.coincore.copyAndPut
import piuk.blockchain.android.coincore.impl.txEngine.PricedQuote
import piuk.blockchain.android.coincore.impl.txEngine.QuotedEngine
import piuk.blockchain.android.coincore.updateTxValidity
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import java.math.BigDecimal
import java.math.RoundingMode

private const val USER_TIER = "USER_TIER"

private val PendingTx.userTier: KycTiers
    get() = (this.engineState[USER_TIER] as KycTiers)

abstract class SwapEngineBase(
    quotesProvider: QuotesProvider,
    private val walletManager: CustodialWalletManager,
    kycTierService: TierService,
    environmentConfig: EnvironmentConfig
) : QuotedEngine(quotesProvider, kycTierService, walletManager, environmentConfig) {

    private lateinit var minApiLimit: Money

    val target: CryptoAccount
        get() = txTarget as CryptoAccount

    override fun targetExchangeRate(): Observable<ExchangeRate> =
        quotesEngine.pricedQuote.map {
            ExchangeRate.CryptoToCrypto(
                from = sourceAccount.asset,
                to = target.asset,
                rate = it.price.toBigDecimal()
            )
        }

    override fun onLimitsForTierFetched(
        tier: KycTiers,
        limits: TransferLimits,
        pendingTx: PendingTx,
        pricedQuote: PricedQuote
    ): PendingTx {
        val exchangeRate = ExchangeRate.CryptoToFiat(
            sourceAccount.asset,
            userFiat,
            exchangeRates.getLastPrice(sourceAccount.asset, userFiat).toBigDecimal()
        )

        minApiLimit = exchangeRate.inverse()
            .convert(limits.minLimit) as CryptoValue

        return pendingTx.copy(
            minLimit = minLimit(pricedQuote.price),
            maxLimit = (exchangeRate.inverse().convert(limits.maxLimit) as CryptoValue).withUserDpRounding(
                RoundingMode.FLOOR),
            engineState = pendingTx.engineState.copyAndPut(USER_TIER, tier)
        )
    }

    override fun doValidateAmount(pendingTx: PendingTx): Single<PendingTx> =
        validateAmount(pendingTx).updateTxValidity(pendingTx)

    private fun validateAmount(pendingTx: PendingTx): Completable {
        return sourceAccount.actionableBalance.flatMapCompletable { balance ->
            if (pendingTx.amount <= balance) {
                if (pendingTx.maxLimit != null && pendingTx.minLimit != null) {
                    when {
                        pendingTx.amount < pendingTx.minLimit -> throw TxValidationFailure(
                            ValidationState.UNDER_MIN_LIMIT)
                        pendingTx.amount > pendingTx.maxLimit -> throw validationFailureForTier(pendingTx)
                        else -> Completable.complete()
                    }
                } else {
                    throw TxValidationFailure(ValidationState.UNKNOWN_ERROR)
                }
            } else {
                throw TxValidationFailure(ValidationState.INSUFFICIENT_FUNDS)
            }
        }
    }

    private fun validationFailureForTier(pendingTx: PendingTx) =
        if (pendingTx.userTier.isApprovedFor(KycTierLevel.GOLD)) {
            TxValidationFailure(ValidationState.OVER_GOLD_TIER_LIMIT)
        } else {
            TxValidationFailure(ValidationState.OVER_SILVER_TIER_LIMIT)
        }

    override fun doValidateAll(pendingTx: PendingTx): Single<PendingTx> =
        validateAmount(pendingTx).updateTxValidity(pendingTx)

    override fun doBuildConfirmations(pendingTx: PendingTx): Single<PendingTx> {
        return quotesEngine.pricedQuote.firstOrError().flatMap { pricedQuote ->
            Single.just(
                pendingTx.copy(
                    confirmations = listOf(
                        TxConfirmationValue.SwapSourceValue(swappingAssetValue = pendingTx.amount as CryptoValue),
                        TxConfirmationValue.SwapReceiveValue(receiveAmount = CryptoValue.fromMajor(target.asset,
                            pendingTx.amount.toBigDecimal().times(pricedQuote.price.toBigDecimal()))),
                        TxConfirmationValue.SwapExchangeRate(CryptoValue.fromMajor(sourceAccount.asset, BigDecimal.ONE),
                            CryptoValue.fromMajor(target.asset, pricedQuote.price.toBigDecimal())),
                        TxConfirmationValue.From(from = sourceAccount.label),
                        TxConfirmationValue.To(to = txTarget.label),
                        TxConfirmationValue.NetworkFee(
                            fee = pricedQuote.transferQuote.networkFee,
                            type = TxConfirmationValue.NetworkFee.FeeType.WITHDRAWAL_FEE,
                            asset = target.asset
                        ),
                        TxConfirmationValue.NetworkFee(
                            fee = pendingTx.fees,
                            type = TxConfirmationValue.NetworkFee.FeeType.DEPOSIT_FEE,
                            asset = sourceAccount.asset
                        )
                    ),
                    minLimit = minLimit(pricedQuote.price)
                )
            )
        }
    }

    private fun minLimit(price: Money): Money {
        val minAmountToPayFees = minAmountToPayNetworkFees(
            price,
            quotesEngine.getLatestQuote().transferQuote.networkFee,
            quotesEngine.getLatestQuote().transferQuote.staticFee
        )

        return minApiLimit.plus(minAmountToPayFees).withUserDpRounding(RoundingMode.CEILING)
    }

    override fun doRefreshConfirmations(pendingTx: PendingTx): Single<PendingTx> {
        return quotesEngine.pricedQuote.firstOrError().map { pricedQuote ->
            pendingTx.copy(
                minLimit = minLimit(pricedQuote.price)
            ).apply {
                addOrReplaceOption(
                    TxConfirmationValue.NetworkFee(
                        fee = quotesEngine.getLatestQuote().transferQuote.networkFee,
                        type = TxConfirmationValue.NetworkFee.FeeType.WITHDRAWAL_FEE,
                        asset = target.asset
                    )
                )
                addOrReplaceOption(
                    TxConfirmationValue.SwapExchangeRate(
                        CryptoValue.fromMajor(sourceAccount.asset, BigDecimal.ONE),
                        pricedQuote.price
                    )
                )
                addOrReplaceOption(
                    TxConfirmationValue.SwapReceiveValue(receiveAmount = CryptoValue.fromMajor(target.asset,
                        pendingTx.amount.toBigDecimal().times(pricedQuote.price.toBigDecimal())))
                )
            }
        }
    }

    protected fun createOrder(pendingTx: PendingTx): Single<CustodialOrder> =
        target.receiveAddress.flatMap {
            walletManager.createCustodialOrder(
                direction = direction,
                quoteId = quotesEngine.getLatestQuote().transferQuote.id,
                volume = pendingTx.amount,
                destinationAddress = if (direction.requiresDestinationAddress()) it.address else null
            )
        }.doFinally {
            disposeQuotesFetching(pendingTx)
        }

    private fun TransferDirection.requiresDestinationAddress() =
        this == TransferDirection.ON_CHAIN || this == TransferDirection.TO_USERKEY

    private fun minAmountToPayNetworkFees(price: Money, networkFee: Money, staticFee: Money): Money =
        CryptoValue.fromMajor(
            sourceAccount.asset,
            (networkFee.toBigDecimal().divide(price.toBigDecimal(), sourceAccount.asset.dp, RoundingMode.HALF_UP)).plus(
                staticFee.toBigDecimal())
        )
}