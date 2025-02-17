package piuk.blockchain.android.sell

import com.blockchain.nabu.BlockedReason
import com.blockchain.nabu.Feature
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.Tier
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.testutils.EUR
import com.blockchain.testutils.GBP
import com.blockchain.testutils.USD
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import org.junit.Before
import org.junit.Test
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.sell.BuySellFlowNavigator
import piuk.blockchain.android.ui.sell.BuySellIntroAction

class BuySellFlowNavigatorTest {

    private val simpleBuySyncFactory: SimpleBuySyncFactory = mock()
    private val custodialWalletManager: CustodialWalletManager = mock()
    private val userIdentity: UserIdentity = mock {
        on { isVerifiedFor(Feature.TierLevel(Tier.GOLD)) }.thenReturn(Single.just(true))
        on { isEligibleFor(Feature.SimpleBuy) }.thenReturn(Single.just(true))
    }
    private lateinit var subject: BuySellFlowNavigator

    @Before
    fun setUp() {
        subject = BuySellFlowNavigator(
            simpleBuySyncFactory, custodialWalletManager, userIdentity
        )

        whenever(simpleBuySyncFactory.currentState()).thenReturn(
            SimpleBuyState()
        )
    }

    @Test
    fun `when user is not eligible, corresponding state should be propagated to the UI`() {
        whenever(userIdentity.userAccessForFeature(Feature.SimpleBuy)).thenReturn(
            Single.just(
                FeatureAccess.Blocked(
                    BlockedReason.NotEligible
                )
            )
        )

        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf(EUR, GBP)))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy(GBP))
            .thenReturn(Single.just(true))

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.UserNotEligible)
    }

    @Test
    fun `whenBuyStateIsNotPendingCurrencyIsSupportedAndSellIsEnableNormalBuySellUiIsDisplayed`() {
        whenever(userIdentity.userAccessForFeature(Feature.SimpleBuy)).thenReturn(
            Single.just(
                FeatureAccess.Granted()
            )
        )
        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf(EUR, USD)))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy(USD))
            .thenReturn(Single.just(true))

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.DisplayBuySellIntro)
    }

    @Test
    fun `whenBuyStateIsPendingConfirmationOrderIsCancelledAndBuySellUiIsDisplayed`() {
        whenever(simpleBuySyncFactory.currentState()).thenReturn(
            SimpleBuyState(
                id = "ORDERID",
                orderState = OrderState.PENDING_CONFIRMATION
            )
        )

        whenever(userIdentity.userAccessForFeature(Feature.SimpleBuy)).thenReturn(
            Single.just(
                FeatureAccess.Granted()
            )
        )

        whenever(custodialWalletManager.getSupportedFiatCurrencies()).thenReturn(Single.just(listOf(EUR, USD)))
        whenever(custodialWalletManager.isCurrencySupportedForSimpleBuy(USD))
            .thenReturn(Single.just(true))
        whenever(custodialWalletManager.deleteBuyOrder("ORDERID"))
            .thenReturn(Completable.complete())

        val test = subject.navigateTo().test()

        test.assertValue(BuySellIntroAction.DisplayBuySellIntro)
        verify(custodialWalletManager).deleteBuyOrder("ORDERID")
    }
}
