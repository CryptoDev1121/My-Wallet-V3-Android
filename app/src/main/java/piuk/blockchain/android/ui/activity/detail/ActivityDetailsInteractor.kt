package piuk.blockchain.android.ui.activity.detail

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.datamanagers.OrderState
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Single
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CustodialActivitySummaryItem
import piuk.blockchain.android.coincore.NonCustodialActivitySummaryItem
import java.text.ParseException
import java.util.Date

class ActivityDetailsInteractor(
    private val coincore: Coincore,
    private val currencyPrefs: CurrencyPrefs,
    private val transactionInputOutputMapper: TransactionInOutMapper
) {

    fun loadCustodialItems(
        custodialActivitySummaryItem: CustodialActivitySummaryItem
    ): Single<List<ActivityDetailsType>> {
        val list = mutableListOf(
            BuyTransactionId(custodialActivitySummaryItem.txId),
            Created(Date(custodialActivitySummaryItem.timeStampMs)),
            BuyPurchaseAmount(custodialActivitySummaryItem.fundedFiat),
            BuyCryptoWallet(custodialActivitySummaryItem.cryptoCurrency),
            BuyFee(custodialActivitySummaryItem.fee),
            // TODO this will change when we add cards, but for now it's the only supported type
            BuyPaymentMethod("Bank Wire Transfer")
        )
        if (custodialActivitySummaryItem.status == OrderState.AWAITING_FUNDS ||
            custodialActivitySummaryItem.status == OrderState.PENDING_EXECUTION) {
            list.add(CancelAction())
        }

        return Single.just(list.toList())
    }

    fun getCustodialActivityDetails(
        cryptoCurrency: CryptoCurrency,
        txHash: String
    ): CustodialActivitySummaryItem? = coincore[cryptoCurrency].findCachedActivityItem(
        txHash
    ) as? CustodialActivitySummaryItem

    fun getNonCustodialActivityDetails(
        cryptoCurrency: CryptoCurrency,
        txHash: String
    ): NonCustodialActivitySummaryItem? = coincore[cryptoCurrency].findCachedActivityItem(
        txHash
    ) as? NonCustodialActivitySummaryItem

    fun loadCreationDate(
        activitySummaryItem: ActivitySummaryItem
    ): Date? = try {
        Date(activitySummaryItem.timeStampMs)
    } catch (e: ParseException) {
        null
    }

    fun loadFeeItems(
        item: NonCustodialActivitySummaryItem
    ) = item.totalFiatWhenExecuted(currencyPrefs.selectedFiatCurrency)
        .flatMap { fiatValue ->
            transactionInputOutputMapper.transformInputAndOutputs(item).map {
                listOf(
                    Amount(item.totalCrypto),
                    Value(fiatValue),
                    addSingleOrMultipleFromAddresses(it),
                    FeeForTransaction("TODO"),
                    Description(),
                    Action()
                )
            }
        }

    fun loadReceivedItems(
        item: NonCustodialActivitySummaryItem
    ) = item.totalFiatWhenExecuted(currencyPrefs.selectedFiatCurrency)
        .flatMap { fiatValue ->
            transactionInputOutputMapper.transformInputAndOutputs(item).map {
                listOf(
                    Amount(item.totalCrypto),
                    Value(fiatValue),
                    addSingleOrMultipleFromAddresses(it),
                    addSingleOrMultipleToAddresses(it),
                    Description(),
                    Action()
                )
            }
        }

    fun loadTransferItems(
        item: NonCustodialActivitySummaryItem
    ) = item.totalFiatWhenExecuted(currencyPrefs.selectedFiatCurrency)
        .flatMap { fiatValue ->
            transactionInputOutputMapper.transformInputAndOutputs(item).map {
                listOf(
                    Amount(item.totalCrypto),
                    Value(fiatValue),
                    addSingleOrMultipleFromAddresses(it),
                    addSingleOrMultipleToAddresses(it),
                    Description(),
                    Action()
                )
            }
        }

    fun loadConfirmedSentItems(
        item: NonCustodialActivitySummaryItem
    ) = item.fee.singleOrError().flatMap { cryptoValue ->
        item.totalFiatWhenExecuted(currencyPrefs.selectedFiatCurrency).flatMap { fiatValue ->
            transactionInputOutputMapper.transformInputAndOutputs(item).map {
                listOf(
                    Amount(item.totalCrypto),
                    Fee(cryptoValue),
                    Value(fiatValue),
                    addSingleOrMultipleFromAddresses(it),
                    addSingleOrMultipleToAddresses(it),
                    Description(),
                    Action()
                )
            }
        }
    }

    fun loadUnconfirmedSentItems(
        item: NonCustodialActivitySummaryItem
    ) = item.fee.singleOrError().flatMap { cryptoValue ->
        transactionInputOutputMapper.transformInputAndOutputs(item).map {
            listOf(
                Amount(item.totalCrypto),
                Fee(cryptoValue),
                addSingleOrMultipleFromAddresses(it),
                addSingleOrMultipleToAddresses(it),
                Description(),
                Action()
            )
        }
    }

    private fun addSingleOrMultipleFromAddresses(
        it: TransactionInOutDetails
    ) = if (it.inputs.size == 1) {
        From(it.inputs[0].address)
    } else {
        From(it.inputs.joinToString("\n"))
    }

    private fun addSingleOrMultipleToAddresses(
        it: TransactionInOutDetails
    ) = if (it.outputs.size == 1) {
        To(it.outputs[0].address)
    } else {
        To(it.outputs.joinToString("\n"))
    }
}