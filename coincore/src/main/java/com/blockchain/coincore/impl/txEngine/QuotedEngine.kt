package com.blockchain.coincore.impl.txEngine

import com.blockchain.api.NabuApiException
import com.blockchain.api.NabuErrorCodes
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.PendingTx
import com.blockchain.coincore.SingleAccount
import com.blockchain.coincore.TransactionTarget
import com.blockchain.coincore.TxEngine
import com.blockchain.coincore.TxResult
import com.blockchain.coincore.TxValidationFailure
import com.blockchain.coincore.ValidationState
import com.blockchain.coincore.copyAndPut
import com.blockchain.coincore.impl.makeExternalAssetAddress
import com.blockchain.core.limits.LegacyLimits
import com.blockchain.core.limits.LimitsDataManager
import com.blockchain.core.limits.TxLimits
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.nabu.Feature
import com.blockchain.nabu.Tier
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrder
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.TransferDirection
import info.blockchain.balance.AssetCategory
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.Currency
import info.blockchain.balance.Money
import info.blockchain.balance.asFiatCurrencyOrThrow
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import java.math.RoundingMode
import piuk.blockchain.androidcore.utils.extensions.emptySubscribe
import piuk.blockchain.androidcore.utils.extensions.thenSingle

const val QUOTE_SUB = "quote_sub"
private val PendingTx.quoteSub: Disposable?
    get() = (this.engineState[QUOTE_SUB] as? Disposable)

abstract class QuotedEngine(
    protected val quotesEngine: TransferQuotesEngine,
    private val userIdentity: UserIdentity,
    private val walletManager: CustodialWalletManager,
    private val limitsDataManager: LimitsDataManager,
    private val productType: Product
) : TxEngine() {
    protected abstract val direction: TransferDirection

    protected abstract val availableBalance: Single<Money>

    private val targetAsset
        get() = (txTarget as SingleAccount).currency

    private val userIsGoldVerified: Single<Boolean>
        get() = userIdentity.isVerifiedFor(Feature.TierLevel(Tier.GOLD))

    protected fun updateLimits(
        fiat: Currency,
        pendingTx: PendingTx,
        pricedQuote: PricedQuote
    ): Single<PendingTx> =
        limitsDataManager.getLimits(
            outputCurrency = sourceAsset,
            sourceCurrency = sourceAsset,
            targetCurrency = (txTarget as SingleAccount).currency,
            legacyLimits = walletManager.getProductTransferLimits(
                fiat.asFiatCurrencyOrThrow(),
                productType,
                direction
            )
                .map {
                    it as LegacyLimits
                },
            sourceAccountType = direction.sourceAccountType(),
            targetAccountType = direction.targetAccountType()
        ).map { limits ->
            onLimitsForTierFetched(limits, pendingTx, pricedQuote)
        }

    protected val pair: CurrencyPair
        get() = CurrencyPair(sourceAsset, targetAsset)

    protected fun validationFailureForTier(): Completable {
        return userIsGoldVerified.flatMapCompletable {
            if (it) {
                Completable.error(TxValidationFailure(ValidationState.OVER_GOLD_TIER_LIMIT))
            } else {
                Completable.error(TxValidationFailure(ValidationState.OVER_SILVER_TIER_LIMIT))
            }
        }
    }

    protected abstract fun onLimitsForTierFetched(
        limits: TxLimits,
        pendingTx: PendingTx,
        pricedQuote: PricedQuote
    ): PendingTx

    protected fun Single<PendingTx>.clearConfirmations(): Single<PendingTx> =
        map {
            it.quoteSub?.dispose()
            it.copy(
                confirmations = emptyList(),
                engineState = it.engineState.toMutableMap().apply { remove(QUOTE_SUB) }.toMap()
            )
        }

    override fun start(
        sourceAccount: BlockchainAccount,
        txTarget: TransactionTarget,
        exchangeRates: ExchangeRatesDataManager,
        refreshTrigger: RefreshTrigger
    ) {
        super.start(sourceAccount, txTarget, exchangeRates, refreshTrigger)
        quotesEngine.start(direction, pair)
    }

    protected fun Single<PendingTx>.updateQuotePrice(): Single<PendingTx> =
        doOnSuccess {
            quotesEngine.updateAmount(it.amount)
        }

    override fun startConfirmationsUpdate(pendingTx: PendingTx): Single<PendingTx> =
        startQuotesFetchingIfNotStarted(pendingTx)

    private fun startQuotesFetching(): Disposable =
        quotesEngine.pricedQuote.doOnNext {
            refreshConfirmations(true)
        }.emptySubscribe()

    private fun startQuotesFetchingIfNotStarted(pendingTx: PendingTx): Single<PendingTx> =
        Single.just(
            if (pendingTx.quoteSub == null) {
                pendingTx.copy(
                    engineState = pendingTx.engineState.copyAndPut(
                        QUOTE_SUB, startQuotesFetching()
                    )
                )
            } else {
                pendingTx
            }
        )

    protected fun disposeQuotesFetching(pendingTx: PendingTx) {
        pendingTx.quoteSub?.dispose()
        quotesEngine.stop()
    }

    protected fun OnChainTxEngineBase.startFromQuote(quote: PricedQuote) {
        start(
            sourceAccount = this@QuotedEngine.sourceAccount,
            txTarget = makeExternalAssetAddress(
                asset = this@QuotedEngine.sourceAsset as AssetInfo,
                address = quote.transferQuote.sampleDepositAddress
            ),
            exchangeRates = this@QuotedEngine.exchangeRates
        )
    }

    protected fun OnChainTxEngineBase.restartFromOrder(order: CustodialOrder, pendingTx: PendingTx): Single<PendingTx> =
        restart(
            txTarget = makeExternalAssetAddress(
                asset = sourceAsset as AssetInfo,
                address = order.depositAddress ?: throw IllegalStateException("Missing deposit address"),
                postTransactions = { Completable.complete() }
            ),
            pendingTx = pendingTx
        )

    protected fun Single<TxResult>.updateOrderStatus(orderId: String): Single<TxResult> = onErrorResumeNext { error ->
        walletManager.updateOrder(orderId, false).onErrorComplete().toSingle {
            throw error
        }
    }
        .flatMap { result ->
            walletManager.updateOrder(orderId, true).onErrorComplete().thenSingle {
                Single.just(result)
            }
        }

    override fun stop(pendingTx: PendingTx) {
        disposeQuotesFetching(pendingTx)
    }

    // Quotes api returns the error code for pending orders that's why this method belongs here
    protected fun Single<PendingTx>.handlePendingOrdersError(pendingTx: PendingTx): Single<PendingTx> =
        this.onErrorResumeNext {
            if (it is NabuApiException && it.getErrorCode() == NabuErrorCodes.PendingOrdersLimitReached) {
                Single.just(
                    pendingTx.copy(
                        validationState = ValidationState.PENDING_ORDERS_LIMIT_REACHED
                    )
                )
            } else Single.error(it)
        }

    protected fun Money.withUserDpRounding(roundingMode: RoundingMode): CryptoValue =
        (this as? CryptoValue)?.let {
            CryptoValue.fromMajor(it.currency, it.toBigDecimal().setScale(CryptoValue.DISPLAY_DP, roundingMode))
        } ?: throw IllegalStateException("Method only support cryptovalues")
}

private fun TransferDirection.sourceAccountType(): AssetCategory {
    return when (this) {
        TransferDirection.FROM_USERKEY,
        TransferDirection.ON_CHAIN -> AssetCategory.NON_CUSTODIAL
        TransferDirection.INTERNAL,
        TransferDirection.TO_USERKEY -> AssetCategory.CUSTODIAL
    }
}

private fun TransferDirection.targetAccountType(): AssetCategory {
    return when (this) {
        TransferDirection.TO_USERKEY,
        TransferDirection.ON_CHAIN -> AssetCategory.NON_CUSTODIAL
        TransferDirection.INTERNAL,
        TransferDirection.FROM_USERKEY -> AssetCategory.CUSTODIAL
    }
}
