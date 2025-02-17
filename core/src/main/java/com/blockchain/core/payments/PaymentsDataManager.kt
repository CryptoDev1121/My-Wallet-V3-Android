package com.blockchain.core.payments

import android.annotation.SuppressLint
import com.blockchain.api.adapters.ApiError
import com.blockchain.api.nabu.data.AddressRequest
import com.blockchain.api.paymentmethods.models.AddNewCardBodyRequest
import com.blockchain.api.paymentmethods.models.BankInfoResponse
import com.blockchain.api.paymentmethods.models.BankMediaResponse
import com.blockchain.api.paymentmethods.models.BankMediaResponse.Companion.ICON
import com.blockchain.api.paymentmethods.models.BankTransferChargeAttributes
import com.blockchain.api.paymentmethods.models.BankTransferChargeResponse
import com.blockchain.api.paymentmethods.models.BankTransferPaymentAttributes
import com.blockchain.api.paymentmethods.models.BankTransferPaymentBody
import com.blockchain.api.paymentmethods.models.CardProviderResponse
import com.blockchain.api.paymentmethods.models.CardResponse
import com.blockchain.api.paymentmethods.models.CreateLinkBankResponse
import com.blockchain.api.paymentmethods.models.EveryPayCardCredentialsResponse
import com.blockchain.api.paymentmethods.models.GooglePayResponse
import com.blockchain.api.paymentmethods.models.Limits
import com.blockchain.api.paymentmethods.models.LinkedBankTransferResponse
import com.blockchain.api.paymentmethods.models.OpenBankingTokenBody
import com.blockchain.api.paymentmethods.models.PaymentMethodResponse
import com.blockchain.api.paymentmethods.models.ProviderAccountAttrs
import com.blockchain.api.paymentmethods.models.SimpleBuyConfirmationAttributes
import com.blockchain.api.paymentmethods.models.UpdateProviderAccountBody
import com.blockchain.api.services.MobilePaymentType
import com.blockchain.api.services.PaymentMethodDetails
import com.blockchain.api.services.PaymentMethodsService
import com.blockchain.api.services.PaymentsService
import com.blockchain.api.services.toMobilePaymentType
import com.blockchain.auth.AuthHeaderProvider
import com.blockchain.core.custodial.TradingBalanceDataManager
import com.blockchain.core.payments.cache.LinkedCardsStore
import com.blockchain.core.payments.cards.CardsCache
import com.blockchain.core.payments.model.BankPartner
import com.blockchain.core.payments.model.BankState
import com.blockchain.core.payments.model.BankTransferDetails
import com.blockchain.core.payments.model.BankTransferStatus
import com.blockchain.core.payments.model.CardProvider
import com.blockchain.core.payments.model.CardToBeActivated
import com.blockchain.core.payments.model.EveryPayCredentials
import com.blockchain.core.payments.model.FundsLock
import com.blockchain.core.payments.model.FundsLocks
import com.blockchain.core.payments.model.LinkBankTransfer
import com.blockchain.core.payments.model.LinkedBank
import com.blockchain.core.payments.model.LinkedBankErrorState
import com.blockchain.core.payments.model.LinkedBankState
import com.blockchain.core.payments.model.Partner
import com.blockchain.core.payments.model.PartnerCredentials
import com.blockchain.core.payments.model.PaymentMethodDetailsError
import com.blockchain.core.payments.model.PaymentMethodsError
import com.blockchain.featureflag.FeatureFlag
import com.blockchain.nabu.common.extensions.wrapErrorMessage
import com.blockchain.nabu.datamanagers.BillingAddress
import com.blockchain.nabu.datamanagers.PaymentLimits
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.custodialwalletimpl.CardStatus
import com.blockchain.nabu.datamanagers.custodialwalletimpl.PaymentMethodType
import com.blockchain.nabu.datamanagers.toSupportedPartner
import com.blockchain.outcome.Outcome
import com.blockchain.outcome.mapLeft
import com.blockchain.payments.googlepay.manager.GooglePayManager
import com.blockchain.payments.googlepay.manager.request.GooglePayRequestBuilder
import com.blockchain.payments.googlepay.manager.request.allowedAuthMethods
import com.blockchain.payments.googlepay.manager.request.allowedCardNetworks
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.store.StoreRequest
import com.blockchain.store.StoreResponse
import com.blockchain.store.firstOutcome
import com.blockchain.store.mapData
import com.blockchain.utils.toZonedDateTime
import com.braintreepayments.cardform.utils.CardType
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.Currency
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.Money
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.io.Serializable
import java.math.BigInteger
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.rxSingle
import piuk.blockchain.androidcore.utils.extensions.rxSingleOutcome

interface PaymentsDataManager {
    suspend fun getPaymentMethodDetailsForId(
        paymentId: String
    ): Outcome<PaymentMethodDetailsError, PaymentMethodDetails>

    fun getWithdrawalLocks(localCurrency: Currency): Single<FundsLocks>

    fun getAvailablePaymentMethodsTypes(
        fiatCurrency: FiatCurrency,
        fetchSddLimits: Boolean,
        onlyEligible: Boolean
    ): Single<List<PaymentMethodTypeWithEligibility>>

    fun getEligiblePaymentMethodTypes(
        fiatCurrency: FiatCurrency
    ): Single<List<EligiblePaymentMethodType>>

    fun getLinkedPaymentMethods(
        currency: FiatCurrency
    ): Single<List<LinkedPaymentMethod>>

    fun getLinkedCards(
        request: StoreRequest,
        vararg states: CardStatus
    ): Flow<StoreResponse<PaymentMethodsError, List<LinkedPaymentMethod.Card>>>

    fun getLinkedCards(vararg states: CardStatus): Single<List<LinkedPaymentMethod.Card>>

    fun getLinkedBank(id: String): Single<LinkedBank>

    fun addNewCard(
        fiatCurrency: FiatCurrency,
        billingAddress: BillingAddress,
        paymentMethodTokens: Map<String, String>? = null
    ): Single<CardToBeActivated>

    fun deleteCard(cardId: String): Completable

    fun getLinkedBanks(): Single<List<LinkedPaymentMethod.Bank>>

    fun removeBank(bank: LinkedPaymentMethod.Bank): Completable

    fun linkBank(currency: FiatCurrency): Single<LinkBankTransfer>

    fun updateSelectedBankAccount(
        linkingId: String,
        providerAccountId: String,
        accountId: String,
        attributes: ProviderAccountAttrs
    ): Completable

    fun startBankTransfer(
        id: String,
        amount: Money,
        currency: String,
        callback: String? = null
    ): Single<String>

    fun updateOpenBankingConsent(url: String, token: String): Completable

    fun getBankTransferCharge(paymentId: String): Single<BankTransferDetails>

    fun canTransactWithBankMethods(fiatCurrency: FiatCurrency): Single<Boolean>

    fun activateCard(cardId: String, attributes: SimpleBuyConfirmationAttributes): Single<PartnerCredentials>

    fun getCardDetails(cardId: String): Single<PaymentMethod.Card>

    fun getGooglePayTokenizationParameters(currency: String): Single<GooglePayResponse>
}

class PaymentsDataManagerImpl(
    private val paymentsService: PaymentsService,
    private val paymentMethodsService: PaymentMethodsService,
    private val linkedCardsStore: LinkedCardsStore,
    private val cardsCache: CardsCache,
    private val cachingStoreFeatureFlag: FeatureFlag,
    private val tradingBalanceDataManager: TradingBalanceDataManager,
    private val assetCatalogue: AssetCatalogue,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val authenticator: AuthHeaderProvider,
    private val googlePayManager: GooglePayManager,
    private val googlePayFeatureFlag: FeatureFlag,
) : PaymentsDataManager {

    private val googlePayEnabled: Single<Boolean> by lazy {
        googlePayFeatureFlag.enabled.cache()
    }

    override suspend fun getPaymentMethodDetailsForId(
        paymentId: String
    ): Outcome<PaymentMethodDetailsError, PaymentMethodDetails> {
        // TODO Turn getAuthHeader() into a suspension function
        val auth = authenticator.getAuthHeader().await()
        return paymentsService.getPaymentMethodDetailsForId(auth, paymentId).mapLeft { apiError: ApiError ->
            when (apiError) {
                is ApiError.HttpError -> PaymentMethodDetailsError.REQUEST_FAILED
                is ApiError.NetworkError -> PaymentMethodDetailsError.SERVICE_UNAVAILABLE
                is ApiError.UnknownApiError -> PaymentMethodDetailsError.UNKNOWN
                is ApiError.KnownError -> PaymentMethodDetailsError.REQUEST_FAILED
            }
        }
    }

    override fun getWithdrawalLocks(localCurrency: Currency): Single<FundsLocks> =
        authenticator.getAuthHeader().flatMap {
            paymentsService.getWithdrawalLocks(it, localCurrency.networkTicker)
                .map { locks ->
                    FundsLocks(
                        onHoldTotalAmount = Money.fromMinor(localCurrency, locks.value.toBigInteger()),
                        locks = locks.locks.map { lock ->
                            FundsLock(
                                amount = Money.fromMinor(localCurrency, lock.value.toBigInteger()),
                                date = lock.date.toZonedDateTime()
                            )
                        }
                    )
                }
        }

    override fun getAvailablePaymentMethodsTypes(
        fiatCurrency: FiatCurrency,
        fetchSddLimits: Boolean,
        onlyEligible: Boolean
    ): Single<List<PaymentMethodTypeWithEligibility>> = authenticator.getAuthHeader().flatMap { authToken ->
        Single.zip(
            paymentMethodsService.getAvailablePaymentMethodsTypes(
                authorization = authToken,
                currency = fiatCurrency.networkTicker,
                tier = if (fetchSddLimits) SDD_ELIGIBLE_TIER else null,
                eligibleOnly = onlyEligible
            ),
            googlePayEnabled,
            rxSingle {
                googlePayManager.checkIfGooglePayIsAvailable(
                    GooglePayRequestBuilder.buildForPaymentStatus(allowedAuthMethods, allowedCardNetworks)
                )
            }
        ) { methods, isGooglePayFeatureFlagEnabled, isGooglePayAvailableOnDevice ->
            if (isGooglePayFeatureFlagEnabled && isGooglePayAvailableOnDevice) {
                return@zip methods.toMutableList().apply {
                    val googlePayPaymentMethod = this.firstOrNull {
                        it.mobilePayment?.any { payment ->
                            payment.equals(PaymentMethodResponse.GOOGLE_PAY, true)
                        } ?: false
                    }
                    googlePayPaymentMethod?.let {
                        this.add(
                            PaymentMethodResponse(
                                type = PaymentMethodResponse.GOOGLE_PAY,
                                eligible = it.eligible,
                                visible = it.visible,
                                limits = it.limits,
                                subTypes = it.subTypes,
                                currency = it.currency
                            )
                        )
                    }
                }
            } else {
                return@zip methods
            }
        }
    }.map { methods ->
        methods.filter { it.visible }
            .filter { it.eligible || !onlyEligible }
    }.doOnSuccess {
        updateSupportedCards(it)
    }.map { methods ->
        val paymentMethodsTypes = methods
            .map { it.toAvailablePaymentMethodType(fiatCurrency) }
            .filterNot { it.type == PaymentMethodType.UNKNOWN }
            .filter {
                when (it.type) {
                    PaymentMethodType.BANK_ACCOUNT ->
                        SUPPORTED_WIRE_TRANSFER_CURRENCIES.contains(it.currency.networkTicker)
                    PaymentMethodType.FUNDS -> SUPPORTED_FUNDS_CURRENCIES.contains(it.currency.networkTicker)
                    PaymentMethodType.BANK_TRANSFER,
                    PaymentMethodType.PAYMENT_CARD,
                    PaymentMethodType.GOOGLE_PAY,
                    PaymentMethodType.UNKNOWN -> true
                }
            }

        paymentMethodsTypes
    }

    override fun getEligiblePaymentMethodTypes(fiatCurrency: FiatCurrency): Single<List<EligiblePaymentMethodType>> =
        getAvailablePaymentMethodsTypes(
            fiatCurrency = fiatCurrency,
            fetchSddLimits = false,
            onlyEligible = true
        ).map { available ->
            available.map { EligiblePaymentMethodType(it.type, it.currency) }
        }

    private fun updateSupportedCards(types: List<PaymentMethodResponse>) {
        val cardTypes =
            types
                .asSequence()
                .filter { it.eligible && it.type.toPaymentMethodType() == PaymentMethodType.PAYMENT_CARD }
                .filter { it.subTypes.isNullOrEmpty().not() }
                .mapNotNull { it.subTypes }
                .flatten().distinct()
                .toList()
        simpleBuyPrefs.updateSupportedCards(cardTypes.joinToString())
    }

    override fun getLinkedPaymentMethods(
        currency: FiatCurrency
    ): Single<List<LinkedPaymentMethod>> =
        authenticator.getAuthHeader().flatMap { authToken ->
            Single.zip(
                tradingBalanceDataManager.getBalanceForCurrency(currency).firstOrError(),
                getLinkedCards(),
                paymentMethodsService.getBanks(authToken).onErrorReturn { emptyList() }
            ) { fundsResponse, cards, linkedBanks ->
                val funds = listOf(LinkedPaymentMethod.Funds(fundsResponse.total, currency))
                val banks = linkedBanks.mapNotNull { it.toPaymentMethod() }

                (cards + funds + banks)
            }
        }

    override fun getLinkedCards(
        request: StoreRequest,
        vararg states: CardStatus
    ): Flow<StoreResponse<PaymentMethodsError, List<LinkedPaymentMethod.Card>>> =
        linkedCardsStore.stream(request)
            .mapData {
                it.filter { states.contains(it.state.toCardStatus()) || states.isEmpty() }
                    .map { it.toPaymentMethod() }
            }

    override fun getLinkedCards(
        vararg states: CardStatus
    ): Single<List<LinkedPaymentMethod.Card>> =
        cachingStoreFeatureFlag.enabled.onErrorReturnItem(false).flatMap { enabled ->
            if (enabled) {
                rxSingleOutcome {
                    getLinkedCards(StoreRequest.Fresh, *states).firstOutcome()
                        .mapLeft {
                            when (it) {
                                is PaymentMethodsError.RequestFailed -> Exception(it.message)
                            }
                        }
                }
            } else {
                cardsCache.cards().map { cardsResponse ->
                    cardsResponse
                        .filter { states.contains(it.state.toCardStatus()) || states.isEmpty() }
                        .map { it.toPaymentMethod() }
                }
            }
        }

    override fun getLinkedBank(id: String): Single<LinkedBank> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.getLinkedBank(
                authorization = authToken,
                id = id
            ).map {
                it.toLinkedBank()
            }
        }

    override fun addNewCard(
        fiatCurrency: FiatCurrency,
        billingAddress: BillingAddress,
        paymentMethodTokens: Map<String, String>?
    ): Single<CardToBeActivated> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.addNewCard(
                authorization = authToken,
                addNewCardBodyRequest = AddNewCardBodyRequest(
                    fiatCurrency.networkTicker,
                    billingAddress.toAddressRequest(),
                    paymentMethodTokens
                )
            )
        }.map {
            CardToBeActivated(cardId = it.id, partner = it.partner.toPartner())
        }.doOnSuccess {
            linkedCardsStore.markAsStale()
            cardsCache.invalidate()
        }

    override fun activateCard(
        cardId: String,
        attributes: SimpleBuyConfirmationAttributes
    ): Single<PartnerCredentials> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.activateCard(authToken, cardId, attributes)
        }.map { response ->
            // Either everypay or cardProvider will be provided by ActivateCardResponse, never both
            when {
                response.everypay != null -> PartnerCredentials.EverypayPartner(
                    everyPay = response.everypay!!.toEverypayCredentials()
                )
                response.cardProvider != null -> PartnerCredentials.CardProviderPartner(
                    cardProvider = response.cardProvider!!.toCardProvider()
                )
                else -> PartnerCredentials.Unknown
            }
        }.doOnSuccess {
            linkedCardsStore.markAsStale()
            cardsCache.invalidate()
        }.wrapErrorMessage()

    override fun getCardDetails(cardId: String): Single<PaymentMethod.Card> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.getCardDetails(authToken, cardId)
        }.map { cardsResponse ->
            cardsResponse.toPaymentMethod().toCardPaymentMethod(
                cardLimits = PaymentLimits(
                    BigInteger.ZERO,
                    BigInteger.ZERO,
                    FiatCurrency.fromCurrencyCode(cardsResponse.currency)
                )
            )
        }

    override fun getGooglePayTokenizationParameters(currency: String): Single<GooglePayResponse> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.getGooglePayInfo(authToken, currency)
        }

    override fun deleteCard(cardId: String): Completable =
        authenticator.getAuthHeader().flatMapCompletable { authToken ->
            paymentMethodsService.deleteCard(authToken, cardId)
        }.doOnComplete {
            linkedCardsStore.markAsStale()
            cardsCache.invalidate()
        }

    override fun getLinkedBanks(): Single<List<LinkedPaymentMethod.Bank>> {
        return authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.getBanks(authToken)
        }.map { banksResponse ->
            banksResponse.mapNotNull { it.toPaymentMethod() }
        }
    }

    override fun removeBank(bank: LinkedPaymentMethod.Bank): Completable =
        authenticator.getAuthHeader().flatMapCompletable { authToken ->
            when (bank.type) {
                PaymentMethodType.BANK_ACCOUNT -> paymentMethodsService.removeBeneficiary(authToken, bank.id)
                PaymentMethodType.BANK_TRANSFER -> paymentMethodsService.removeLinkedBank(authToken, bank.id)
                else -> Completable.error(IllegalStateException("Unknown Bank type"))
            }
        }

    override fun linkBank(currency: FiatCurrency): Single<LinkBankTransfer> {
        return authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.linkBank(authToken, currency.networkTicker)
        }.flatMap { response ->
            val partner =
                response.partner.toLinkingBankPartner() ?: return@flatMap Single.error<LinkBankTransfer>(
                    IllegalStateException("Partner not Supported")
                )
            val attributes =
                response.attributes ?: return@flatMap Single.error<LinkBankTransfer>(
                    IllegalStateException("Missing attributes")
                )
            Single.just(
                LinkBankTransfer(
                    response.id,
                    partner,
                    partner.attributes(attributes)
                )
            )
        }
    }

    override fun startBankTransfer(
        id: String,
        amount: Money,
        currency: String,
        callback: String?
    ): Single<String> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.startBankTransferPayment(
                authorization = authToken,
                id = id,
                body = BankTransferPaymentBody(
                    amountMinor = amount.toBigInteger().toString(),
                    currency = currency,
                    product = "SIMPLEBUY",
                    attributes = if (callback != null) {
                        BankTransferPaymentAttributes(callback)
                    } else null
                )
            ).map {
                it.paymentId
            }
        }

    override fun updateSelectedBankAccount(
        linkingId: String,
        providerAccountId: String,
        accountId: String,
        attributes: ProviderAccountAttrs
    ): Completable = authenticator.getAuthHeader().flatMapCompletable { authToken ->
        paymentMethodsService.updateAccountProviderId(
            authToken,
            linkingId,
            UpdateProviderAccountBody(attributes)
        )
    }

    override fun updateOpenBankingConsent(
        url: String,
        token: String
    ): Completable =
        authenticator.getAuthHeader().flatMapCompletable { authToken ->
            paymentMethodsService.updateOpenBankingToken(
                url,
                authToken,
                OpenBankingTokenBody(
                    oneTimeToken = token
                )
            )
        }

    override fun getBankTransferCharge(paymentId: String): Single<BankTransferDetails> =
        authenticator.getAuthHeader().flatMap { authToken ->
            paymentMethodsService.getBankTransferCharge(
                authorization = authToken,
                paymentId = paymentId
            ).map {
                it.toBankTransferDetails()
            }
        }

    override fun canTransactWithBankMethods(fiatCurrency: FiatCurrency): Single<Boolean> =
        if (!SUPPORTED_WIRE_TRANSFER_CURRENCIES.contains(fiatCurrency.networkTicker))
            Single.just(false)
        else getAvailablePaymentMethodsTypes(
            fiatCurrency = fiatCurrency,
            fetchSddLimits = false,
            onlyEligible = true
        ).map { available ->
            available.any { it.type == PaymentMethodType.BANK_ACCOUNT || it.type == PaymentMethodType.BANK_TRANSFER }
        }

    // <editor-fold desc="Editor Fold: Network response Mappers">
    private fun CardResponse.toPaymentMethod(): LinkedPaymentMethod.Card {
        return LinkedPaymentMethod.Card(
            cardId = id,
            label = card?.label.orEmpty(),
            endDigits = card?.number.orEmpty(),
            partner = partner.toSupportedPartner(),
            expireDate = card?.let {
                Calendar.getInstance().apply {
                    set(
                        it.expireYear ?: this.get(Calendar.YEAR),
                        it.expireMonth ?: this.get(Calendar.MONTH),
                        0
                    )
                }.time
            } ?: Date(),
            cardType = card?.type?.toCardType() ?: CardType.UNKNOWN,
            status = state.toCardStatus(),
            currency = assetCatalogue.fiatFromNetworkTicker(currency)
                ?: throw IllegalStateException("Unknown currency $currency"),
            mobilePaymentType = mobilePaymentType?.toMobilePaymentType()
        )
    }

    private fun LinkedPaymentMethod.Card.toCardPaymentMethod(cardLimits: PaymentLimits) =
        PaymentMethod.Card(
            cardId = cardId,
            limits = cardLimits,
            label = label,
            endDigits = endDigits,
            partner = partner,
            expireDate = expireDate,
            cardType = cardType,
            status = status,
            isEligible = true,
            mobilePaymentType = mobilePaymentType
        )

    private fun BankInfoResponse.toPaymentMethod(): LinkedPaymentMethod.Bank? {
        return LinkedPaymentMethod.Bank(
            id = id,
            name = name.takeIf { it?.isNotEmpty() == true } ?: accountName.orEmpty(),
            accountEnding = accountNumber ?: "****",
            accountType = bankAccountType.orEmpty(),
            iconUrl = attributes?.media?.find { it.type == BankMediaResponse.ICON }?.source.orEmpty(),
            isBankTransferAccount = isBankTransferAccount,
            state = state.toBankState(),
            currency = assetCatalogue.fiatFromNetworkTicker(currency) ?: return null
        )
    }

    private fun LinkedBankTransferResponse.toLinkedBank(): LinkedBank? {
        val bankPartner = partner.toLinkingBankPartner() ?: return null
        return LinkedBank(
            id = id,
            currency = assetCatalogue.fiatFromNetworkTicker(currency) ?: return null,
            partner = bankPartner,
            state = state.toLinkedBankState(),
            bankName = details?.bankName.orEmpty(),
            accountName = details?.accountName.orEmpty(),
            accountNumber = details?.accountNumber?.replace("x", "").orEmpty(),
            errorStatus = error?.toLinkedBankErrorState() ?: LinkedBankErrorState.NONE,
            accountType = details?.bankAccountType.orEmpty(),
            authorisationUrl = attributes?.authorisationUrl.orEmpty(),
            sortCode = details?.sortCode.orEmpty(),
            accountIban = details?.iban.orEmpty(),
            bic = details?.bic.orEmpty(),
            entity = attributes?.entity.orEmpty(),
            iconUrl = attributes?.media?.find { it.source == ICON }?.source.orEmpty(),
            callbackPath = if (bankPartner == BankPartner.YAPILY) {
                attributes?.callbackPath ?: throw IllegalArgumentException("Missing callbackPath")
            } else {
                attributes?.callbackPath.orEmpty()
            }

        )
    }

    private fun String.toLinkedBankState(): LinkedBankState =
        when (this) {
            LinkedBankTransferResponse.CREATED -> LinkedBankState.CREATED
            LinkedBankTransferResponse.ACTIVE -> LinkedBankState.ACTIVE
            LinkedBankTransferResponse.PENDING,
            LinkedBankTransferResponse.FRAUD_REVIEW,
            LinkedBankTransferResponse.MANUAL_REVIEW -> LinkedBankState.PENDING
            LinkedBankTransferResponse.BLOCKED -> LinkedBankState.BLOCKED
            else -> LinkedBankState.UNKNOWN
        }

    private fun String.toLinkedBankErrorState(): LinkedBankErrorState =
        when (this) {
            LinkedBankTransferResponse.ERROR_ALREADY_LINKED -> LinkedBankErrorState.ACCOUNT_ALREADY_LINKED
            LinkedBankTransferResponse.ERROR_ACCOUNT_INFO_NOT_FOUND -> LinkedBankErrorState.NOT_INFO_FOUND
            LinkedBankTransferResponse.ERROR_ACCOUNT_NOT_SUPPORTED -> LinkedBankErrorState.ACCOUNT_TYPE_UNSUPPORTED
            LinkedBankTransferResponse.ERROR_NAMES_MISMATCHED -> LinkedBankErrorState.NAMES_MISMATCHED
            LinkedBankTransferResponse.ERROR_ACCOUNT_EXPIRED -> LinkedBankErrorState.EXPIRED
            LinkedBankTransferResponse.ERROR_ACCOUNT_REJECTED -> LinkedBankErrorState.REJECTED
            LinkedBankTransferResponse.ERROR_ACCOUNT_FAILURE -> LinkedBankErrorState.FAILURE
            LinkedBankTransferResponse.ERROR_ACCOUNT_INVALID -> LinkedBankErrorState.INVALID
            LinkedBankTransferResponse.ERROR_ACCOUNT_FAILED_INTERNAL -> LinkedBankErrorState.INTERNAL_FAILURE
            LinkedBankTransferResponse.ERROR_ACCOUNT_REJECTED_FRAUD -> LinkedBankErrorState.FRAUD
            else -> LinkedBankErrorState.UNKNOWN
        }

    private fun String.toLinkingBankPartner(): BankPartner? {
        val partner = when (this) {
            CreateLinkBankResponse.YODLEE_PARTNER -> BankPartner.YODLEE
            CreateLinkBankResponse.YAPILY_PARTNER -> BankPartner.YAPILY
            else -> null
        }

        return if (SUPPORTED_BANK_PARTNERS.contains(partner)) {
            partner
        } else null
    }

    private fun Limits.toPaymentLimits(currency: FiatCurrency): PaymentLimits = PaymentLimits(
        min.toBigInteger(),
        max.toBigInteger(),
        currency
    )

    private fun String.toCardType(): CardType = try {
        CardType.valueOf(this)
    } catch (ex: Exception) {
        CardType.UNKNOWN
    }

    private fun String.toBankState(): BankState =
        when (this) {
            BankInfoResponse.ACTIVE -> BankState.ACTIVE
            BankInfoResponse.PENDING -> BankState.PENDING
            BankInfoResponse.BLOCKED -> BankState.BLOCKED
            else -> BankState.UNKNOWN
        }

    private fun String.isActive(): Boolean =
        toCardStatus() == CardStatus.ACTIVE

    private fun String.toCardStatus(): CardStatus =
        when (this) {
            CardResponse.ACTIVE -> CardStatus.ACTIVE
            CardResponse.BLOCKED -> CardStatus.BLOCKED
            CardResponse.PENDING -> CardStatus.PENDING
            CardResponse.CREATED -> CardStatus.CREATED
            CardResponse.EXPIRED -> CardStatus.EXPIRED
            else -> CardStatus.UNKNOWN
        }

    private fun String.toPaymentMethodType(): PaymentMethodType = when (this) {
        PaymentMethodResponse.PAYMENT_CARD -> PaymentMethodType.PAYMENT_CARD
        PaymentMethodResponse.FUNDS -> PaymentMethodType.FUNDS
        PaymentMethodResponse.BANK_TRANSFER -> PaymentMethodType.BANK_TRANSFER
        PaymentMethodResponse.BANK_ACCOUNT -> PaymentMethodType.BANK_ACCOUNT
        PaymentMethodResponse.GOOGLE_PAY -> PaymentMethodType.GOOGLE_PAY
        else -> PaymentMethodType.UNKNOWN
    }

    private fun PaymentMethodResponse.toAvailablePaymentMethodType(
        currency: FiatCurrency
    ): PaymentMethodTypeWithEligibility =
        PaymentMethodTypeWithEligibility(
            eligible = eligible,
            currency = currency,
            type = type.toPaymentMethodType(),
            limits = limits.toPaymentLimits(currency),
            cardFundSources = cardFundSources
        )

    private fun BankTransferChargeResponse.toBankTransferDetails() =
        BankTransferDetails(
            id = this.beneficiaryId,
            amount = Money.fromMinor(
                assetCatalogue.fromNetworkTicker(amount.symbol) ?: throw IllegalArgumentException(
                    "Currency not supported"
                ),
                this.amountMinor.toBigInteger()
            ),
            authorisationUrl = this.extraAttributes.authorisationUrl,
            status = this.state?.toBankTransferStatus() ?: this.extraAttributes.status?.toBankTransferStatus()
                ?: BankTransferStatus.UNKNOWN
        )

    private fun String.toBankTransferStatus() =
        when (this) {
            BankTransferChargeAttributes.CREATED,
            BankTransferChargeAttributes.PRE_CHARGE_REVIEW,
            BankTransferChargeAttributes.PRE_CHARGE_APPROVED,
            BankTransferChargeAttributes.AWAITING_AUTHORIZATION,
            BankTransferChargeAttributes.PENDING,
            BankTransferChargeAttributes.AUTHORIZED,
            BankTransferChargeAttributes.CREDITED -> BankTransferStatus.PENDING
            BankTransferChargeAttributes.FAILED,
            BankTransferChargeAttributes.FRAUD_REVIEW,
            BankTransferChargeAttributes.MANUAL_REVIEW,
            BankTransferChargeAttributes.REJECTED -> BankTransferStatus.ERROR
            BankTransferChargeAttributes.CLEARED,
            BankTransferChargeAttributes.COMPLETE -> BankTransferStatus.COMPLETE
            else -> BankTransferStatus.UNKNOWN
        }

    private fun BillingAddress.toAddressRequest() = AddressRequest(
        line1 = addressLine1,
        line2 = addressLine2,
        city = city,
        countryCode = countryCode,
        postCode = postCode,
        state = state
    )

    private fun EveryPayCardCredentialsResponse.toEverypayCredentials() =
        EveryPayCredentials(
            apiUsername,
            mobileToken,
            paymentLink
        )

    private fun CardProviderResponse.toCardProvider() =
        CardProvider(
            cardAcquirerName,
            cardAcquirerAccountCode,
            apiUserID.orEmpty(),
            apiToken.orEmpty(),
            paymentLink.orEmpty(),
            paymentState.orEmpty(),
            paymentReference.orEmpty(),
            orderReference.orEmpty(),
            clientSecret.orEmpty(),
            publishableApiKey.orEmpty()
        )

    private fun String.toPartner(): Partner = when (this) {
        "EVERYPAY" -> Partner.EVERYPAY
        "CARDPROVIDER" -> Partner.CARDPROVIDER
        else -> Partner.UNKNOWN
    }

    // </editor-fold>

    companion object {
        private val SUPPORTED_BANK_PARTNERS = listOf(
            BankPartner.YAPILY, BankPartner.YODLEE
        )
        private val SUPPORTED_FUNDS_CURRENCIES = listOf(
            "GBP", "EUR", "USD"
        )
        private val SUPPORTED_WIRE_TRANSFER_CURRENCIES = listOf(
            "GBP", "EUR", "USD"
        )
        private const val SDD_ELIGIBLE_TIER = 3
    }
}

sealed class LinkedPaymentMethod(
    val type: PaymentMethodType,
    open val currency: FiatCurrency
) {
    data class Card(
        val cardId: String,
        val label: String,
        val endDigits: String,
        val partner: Partner,
        val expireDate: Date,
        val cardType: CardType,
        val status: CardStatus,
        val cardFundSources: List<String>? = null,
        val mobilePaymentType: MobilePaymentType? = null,
        override val currency: FiatCurrency
    ) : LinkedPaymentMethod(PaymentMethodType.PAYMENT_CARD, currency)

    data class Funds(
        val balance: Money,
        override val currency: FiatCurrency
    ) : LinkedPaymentMethod(PaymentMethodType.FUNDS, currency)

    data class Bank(
        val id: String,
        val name: String,
        val accountEnding: String,
        val accountType: String,
        val iconUrl: String,
        val isBankTransferAccount: Boolean,
        val state: BankState,
        override val currency: FiatCurrency
    ) : LinkedPaymentMethod(
        if (isBankTransferAccount) PaymentMethodType.BANK_TRANSFER
        else PaymentMethodType.BANK_ACCOUNT,
        currency
    ),
        Serializable {
        @SuppressLint("DefaultLocale") // Yes, lint is broken
        fun toHumanReadableAccount(): String {
            return accountType.toLowerCase(Locale.getDefault()).capitalize(Locale.getDefault())
        }
    }
}

data class PaymentMethodTypeWithEligibility(
    val eligible: Boolean,
    val currency: FiatCurrency,
    val type: PaymentMethodType,
    val limits: PaymentLimits,
    val cardFundSources: List<String>? = null
)

data class EligiblePaymentMethodType(
    val type: PaymentMethodType,
    val currency: FiatCurrency
)
