package com.blockchain.coincore.evm

import com.blockchain.coincore.ActivitySummaryList
import com.blockchain.coincore.AddressResolver
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.ReceiveAddress
import com.blockchain.coincore.TransactionTarget
import com.blockchain.coincore.TxEngine
import com.blockchain.coincore.TxSourceState
import com.blockchain.coincore.eth.MultiChainAccount
import com.blockchain.coincore.impl.CryptoNonCustodialAccount
import com.blockchain.core.chains.EvmNetwork
import com.blockchain.core.chains.erc20.Erc20DataManager
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.outcome.fold
import com.blockchain.preferences.WalletStatus
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.rx3.rxSingle
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager

class L1EvmNonCustodialAccount(
    payloadManager: PayloadDataManager,
    asset: AssetInfo,
    private val ethDataManager: EthDataManager,
    private val erc20DataManager: Erc20DataManager,
    internal val address: String,
    private val fees: FeeDataManager,
    override val label: String,
    override val exchangeRates: ExchangeRatesDataManager,
    private val walletPreferences: WalletStatus,
    private val custodialWalletManager: CustodialWalletManager,
    override val baseActions: Set<AssetAction>,
    identity: UserIdentity,
    override val addressResolver: AddressResolver,
    override val l1Network: EvmNetwork
) : MultiChainAccount, CryptoNonCustodialAccount(payloadManager, asset, custodialWalletManager, identity) {

    private val hasFunds = AtomicBoolean(false)

    override val isFunded: Boolean
        get() = hasFunds.get()

    override val isDefault: Boolean = true // Only one account, so always default

    override val receiveAddress: Single<ReceiveAddress>
        get() = Single.just(
            MaticAddress(
                address = address,
                label = label
            )
        )

    override fun getOnChainBalance(): Observable<Money> =
        rxSingle {
            ethDataManager.getBalance(l1Network.nodeUrl)
                .fold(
                    onFailure = { Money.fromMajor(currency, BigDecimal.ZERO) },
                    onSuccess = { Money.fromMinor(currency, it) }
                )
        }
            .toObservable()
            .doOnNext { hasFunds.set(it.isPositive) }

    override val activity: Single<ActivitySummaryList>
        get() {
            return Single.zip(
                erc20DataManager.getErc20History(currency, l1Network),
                erc20DataManager.latestBlockNumber(l1Chain = l1Network.networkTicker)
            ) { transactions, latestBlockNumber ->
                transactions.map { transaction ->
                    L1EvmActivitySummaryItem(
                        asset = currency,
                        event = transaction,
                        accountHash = address,
                        exchangeRates = exchangeRates,
                        lastBlockNumber = latestBlockNumber,
                        account = this
                    )
                }
            }.flatMap {
                appendTradeActivity(custodialWalletManager, currency, it)
            }.doOnSuccess {
                setHasTransactions(it.isNotEmpty())
            }
        }

    override val sourceState: Single<TxSourceState>
        get() = super.sourceState.flatMap { state ->
            erc20DataManager.hasUnconfirmedTransactions()
                .map { hasUnconfirmed ->
                    if (hasUnconfirmed) {
                        TxSourceState.TRANSACTION_IN_FLIGHT
                    } else {
                        state
                    }
                }
        }

    override fun createTxEngine(target: TransactionTarget, action: AssetAction): TxEngine =
        L1EvmOnChainTxEngine(
            erc20DataManager = erc20DataManager,
            feeManager = fees,
            requireSecondPassword = erc20DataManager.requireSecondPassword,
            walletPreferences = walletPreferences,
            resolvedAddress = addressResolver.getReceiveAddress(currency, target, action)
        )
}
