package com.blockchain.nabu.datamanagers

import com.blockchain.core.user.NabuUserDataManager
import com.blockchain.domain.eligibility.EligibilityService
import com.blockchain.domain.eligibility.model.EligibleProduct
import com.blockchain.domain.eligibility.model.ProductEligibility
import com.blockchain.domain.eligibility.model.ProductNotEligibleReason
import com.blockchain.domain.eligibility.model.TransactionsLimit
import com.blockchain.nabu.BlockedReason
import com.blockchain.nabu.Feature
import com.blockchain.nabu.FeatureAccess
import com.blockchain.nabu.Tier
import com.blockchain.nabu.datamanagers.repositories.interest.InterestEligibilityProvider
import com.blockchain.nabu.models.responses.nabu.KycTierLevel
import com.blockchain.nabu.models.responses.nabu.KycTierState
import com.blockchain.nabu.models.responses.nabu.KycTiers
import com.blockchain.nabu.models.responses.nabu.Limits
import com.blockchain.nabu.models.responses.nabu.Tiers
import com.blockchain.outcome.Outcome
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NabuUserIdentityTest {

    private val custodialWalletManager: CustodialWalletManager = mock()
    private val interestEligibilityProvider: InterestEligibilityProvider = mock()
    private val simpleBuyEligibilityProvider: SimpleBuyEligibilityProvider = mock()
    private val nabuUserDataManager: NabuUserDataManager = mock()
    private val nabuDataProvider: NabuDataUserProvider = mock()
    private val eligibilityService: EligibilityService = mock()

    private val subject = NabuUserIdentity(
        custodialWalletManager = custodialWalletManager,
        interestEligibilityProvider = interestEligibilityProvider,
        simpleBuyEligibilityProvider = simpleBuyEligibilityProvider,
        nabuUserDataManager = nabuUserDataManager,
        nabuDataProvider = nabuDataProvider,
        eligibilityService = eligibilityService
    )

    @Test
    fun `getHighestApprovedKycTier bronze success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.None, tier2 = KycTierState.None)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.getHighestApprovedKycTier()
            .test()
            .assertValue(Tier.BRONZE)

        verify(nabuUserDataManager).tiers()
    }

    @Test
    fun `getHighestApprovedKycTier silver success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.Rejected)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.getHighestApprovedKycTier()
            .test()
            .assertValue(Tier.SILVER)

        verify(nabuUserDataManager).tiers()
    }

    @Test
    fun `getHighestApprovedKycTier gold success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.Verified)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.getHighestApprovedKycTier()
            .test()
            .assertValue(Tier.GOLD)

        verify(nabuUserDataManager).tiers()
    }

    @Test
    fun `isKycRejected not rejected success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.None)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.isKycRejected()
            .test()
            .assertValue(false)
    }

    @Test
    fun `isKycRejected rejected success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.Rejected)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.isKycRejected()
            .test()
            .assertValue(true)
    }

    @Test
    fun `isKycRejected not pending success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.None)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.isKycPending(Tier.GOLD)
            .test()
            .assertValue(false)
    }

    @Test
    fun `isKycRejected pending success`() {
        val mockTiers = createMockTiers(tier1 = KycTierState.Verified, tier2 = KycTierState.Pending)
        whenever(nabuUserDataManager.tiers()).thenReturn(Single.just(mockTiers))

        subject.isKycPending(Tier.GOLD)
            .test()
            .assertValue(true)
    }

    @Test
    fun `on userAccessForFeature Buy should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.BUY,
            canTransact = true,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = null
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.BUY))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.Buy)
            .test()
            .await()
            .assertValue(FeatureAccess.Granted())
    }

    @Test
    fun `on userAccessForFeature Swap should query eligibility data manager`() = runTest {
        val transactionsLimit = TransactionsLimit.Limited(3, 1)
        val eligibility = ProductEligibility(
            product = EligibleProduct.SWAP,
            canTransact = true,
            maxTransactionsCap = transactionsLimit,
            reasonNotEligible = null
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.SWAP))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.Swap)
            .test()
            .await()
            .assertValue(FeatureAccess.Granted(transactionsLimit))
    }

    @Test
    fun `on userAccessForFeature DepositCrypto should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.DEPOSIT_CRYPTO,
            canTransact = false,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = ProductNotEligibleReason.InsufficientTier.Tier2Required
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.DEPOSIT_CRYPTO))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.DepositCrypto)
            .test()
            .await()
            .assertValue(FeatureAccess.Blocked(BlockedReason.InsufficientTier.Tier2Required))
    }

    @Test
    fun `on userAccessForFeature Sell should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.SELL,
            canTransact = false,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = ProductNotEligibleReason.Sanctions.RussiaEU5
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.SELL))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.Sell)
            .test()
            .await()
            .assertValue(FeatureAccess.Blocked(BlockedReason.Sanctions.RussiaEU5))
    }

    @Test
    fun `on userAccessForFeature DepositFiat should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.DEPOSIT_FIAT,
            canTransact = false,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = ProductNotEligibleReason.Sanctions.RussiaEU5
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.DEPOSIT_FIAT))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.DepositFiat)
            .test()
            .await()
            .assertValue(FeatureAccess.Blocked(BlockedReason.Sanctions.RussiaEU5))
    }

    @Test
    fun `on userAccessForFeature DepositInterest should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.DEPOSIT_INTEREST,
            canTransact = false,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = ProductNotEligibleReason.Sanctions.RussiaEU5
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.DEPOSIT_INTEREST))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.DepositInterest)
            .test()
            .await()
            .assertValue(FeatureAccess.Blocked(BlockedReason.Sanctions.RussiaEU5))
    }

    @Test
    fun `on userAccessForFeature WithdrawFiat should query eligibility data manager`() = runTest {
        val eligibility = ProductEligibility(
            product = EligibleProduct.WITHDRAW_FIAT,
            canTransact = false,
            maxTransactionsCap = TransactionsLimit.Unlimited,
            reasonNotEligible = ProductNotEligibleReason.Sanctions.RussiaEU5
        )
        whenever(eligibilityService.getProductEligibility(EligibleProduct.WITHDRAW_FIAT))
            .thenReturn(Outcome.Success(eligibility))

        subject.userAccessForFeature(Feature.WithdrawFiat)
            .test()
            .await()
            .assertValue(FeatureAccess.Blocked(BlockedReason.Sanctions.RussiaEU5))
    }

    companion object {
        fun createMockTiers(tier1: KycTierState, tier2: KycTierState): KycTiers {
            return KycTiers(
                Tiers(
                    mapOf(
                        KycTierLevel.BRONZE to
                            com.blockchain.nabu.models.responses.nabu.Tier(
                                KycTierState.Verified,
                                Limits(null, null)
                            ),
                        KycTierLevel.SILVER to
                            com.blockchain.nabu.models.responses.nabu.Tier(
                                tier1,
                                Limits(null, null)
                            ),
                        KycTierLevel.GOLD to
                            com.blockchain.nabu.models.responses.nabu.Tier(
                                tier2,
                                Limits(null, null)
                            )
                    )
                )
            )
        }
    }
}
