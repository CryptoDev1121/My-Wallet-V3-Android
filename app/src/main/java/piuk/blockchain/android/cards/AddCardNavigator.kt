package piuk.blockchain.android.cards

import com.blockchain.nabu.datamanagers.PaymentMethod

interface AddCardNavigator {
    fun navigateToCardDetails()
    fun navigateToBillingDetails()
    fun navigateToCardVerification()
    fun exitWithSuccess(card: PaymentMethod.Card)
    fun exitWithError()
}
