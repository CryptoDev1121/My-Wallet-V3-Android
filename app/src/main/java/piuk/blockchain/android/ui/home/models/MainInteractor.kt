package piuk.blockchain.android.ui.home.models

import android.content.Intent
import android.net.Uri
import com.blockchain.banking.BankPaymentApproval
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.core.Database
import com.blockchain.core.payments.PaymentsDataManager
import com.blockchain.core.payments.model.BankTransferDetails
import com.blockchain.core.payments.model.BankTransferStatus
import com.blockchain.deeplinking.navigation.DeeplinkRedirector
import com.blockchain.nabu.Tier
import com.blockchain.nabu.UserIdentity
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.network.PollResult
import com.blockchain.network.PollService
import com.blockchain.preferences.BankLinkingPrefs
import com.blockchain.preferences.OnboardingPrefs
import com.blockchain.preferences.ThePitLinkingPrefs
import info.blockchain.balance.AssetCatalogue
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import java.lang.IllegalStateException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import piuk.blockchain.android.deeplink.DeepLinkProcessor
import piuk.blockchain.android.deeplink.LinkState
import piuk.blockchain.android.domain.usecases.CancelOrderUseCase
import piuk.blockchain.android.scan.QrScanResultProcessor
import piuk.blockchain.android.scan.ScanResult
import piuk.blockchain.android.simplebuy.SimpleBuyState
import piuk.blockchain.android.simplebuy.SimpleBuySyncFactory
import piuk.blockchain.android.ui.auth.newlogin.domain.service.SecureChannelService
import piuk.blockchain.android.ui.home.CredentialsWiper
import piuk.blockchain.android.ui.launcher.DeepLinkPersistence
import piuk.blockchain.android.ui.linkbank.BankAuthDeepLinkState
import piuk.blockchain.android.ui.linkbank.BankAuthFlowState
import piuk.blockchain.android.ui.linkbank.fromPreferencesValue
import piuk.blockchain.android.ui.linkbank.toPreferencesValue
import piuk.blockchain.android.ui.upsell.KycUpgradePromptManager
import thepit.PitLinking
import timber.log.Timber

class MainInteractor internal constructor(
    private val deepLinkProcessor: DeepLinkProcessor,
    private val deeplinkRedirector: DeeplinkRedirector,
    private val deepLinkPersistence: DeepLinkPersistence,
    private val exchangeLinking: PitLinking,
    private val exchangePrefs: ThePitLinkingPrefs,
    private val assetCatalogue: AssetCatalogue,
    private val bankLinkingPrefs: BankLinkingPrefs,
    private val custodialWalletManager: CustodialWalletManager,
    private val paymentsDataManager: PaymentsDataManager,
    private val simpleBuySync: SimpleBuySyncFactory,
    private val userIdentity: UserIdentity,
    private val upsellManager: KycUpgradePromptManager,
    private val database: Database,
    private val credentialsWiper: CredentialsWiper,
    private val qrScanResultProcessor: QrScanResultProcessor,
    private val secureChannelService: SecureChannelService,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val onboardingPrefs: OnboardingPrefs
) {

    fun checkForDeepLinks(intent: Intent): Single<LinkState> =
        deepLinkProcessor.getLink(intent)

    fun checkForDeepLinks(scanResult: ScanResult.HttpUri): Single<LinkState> =
        Single.fromCallable {
            Uri.parse(scanResult.uri).getQueryParameter("link") ?: throw IllegalStateException()
        }.flatMap {
            deepLinkProcessor.getLink(it)
        }

    fun checkForUserWalletErrors(): Completable =
        userIdentity.checkForUserWalletLinkErrors()

    fun getExchangeLinkingState(): Single<Boolean> =
        exchangeLinking.isPitLinked()

    fun getExchangeToWalletLinkId(): String = exchangePrefs.pitToWalletLinkId

    fun getAssetFromTicker(ticker: String?): AssetInfo? =
        ticker?.let {
            assetCatalogue.assetInfoFromNetworkTicker(ticker)
        }

    fun resetLocalBankAuthState() =
        bankLinkingPrefs.setBankLinkingState(
            BankAuthDeepLinkState(bankAuthFlow = BankAuthFlowState.NONE, bankPaymentData = null, bankLinkingInfo = null)
                .toPreferencesValue()
        )

    fun getBankLinkingState(): BankAuthDeepLinkState =
        bankLinkingPrefs.getBankLinkingState().fromPreferencesValue() ?: BankAuthDeepLinkState()

    fun updateBankLinkingState(bankLinkingState: BankAuthDeepLinkState) =
        bankLinkingPrefs.setBankLinkingState(bankLinkingState.toPreferencesValue())

    fun updateOpenBankingConsent(consentToken: String): Completable =
        paymentsDataManager.updateOpenBankingConsent(
            bankLinkingPrefs.getDynamicOneTimeTokenUrl(), consentToken
        ).doOnError {
            resetLocalBankAuthState()
        }

    fun pollForBankTransferCharge(paymentData: BankPaymentApproval): Single<PollResult<BankTransferDetails>> =
        PollService(
            paymentsDataManager.getBankTransferCharge(paymentData.paymentId)
        ) { transferDetails ->
            transferDetails.status != BankTransferStatus.PENDING
        }.start()

    fun getEstimatedDepositCompletionTime(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 3)
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(cal.time)
    }

    fun getSimpleBuySyncLocalState(): SimpleBuyState? = simpleBuySync.currentState()

    fun performSimpleBuySync(): Completable = simpleBuySync.performSync()

    fun checkIfShouldUpsell(action: AssetAction, account: BlockchainAccount?): Single<KycUpgradePromptManager.Type> =
        upsellManager.queryUpsell(action, account)

    fun unpairWallet(): Completable =
        Completable.fromAction {
            credentialsWiper.wipe()
            database.historicRateQueries.clear()
        }

    fun cancelAnyPendingConfirmationBuy(): Completable {
        val currentOrder = simpleBuySync.currentState() ?: return Completable.complete()
        val pendingOrderId = currentOrder.takeIf { it.orderState == OrderState.PENDING_CONFIRMATION }?.id
            ?: return Completable.complete()

        return custodialWalletManager.deleteBuyOrder(pendingOrderId)
            .doOnComplete {
                simpleBuySync.clear()
            }
            .doOnError {
                Timber.e("Failed to cancel buy order $pendingOrderId")
            }
    }

    fun processQrScanResult(decodedData: String): Single<out ScanResult> =
        qrScanResultProcessor.processScan(decodedData)

    fun sendSecureChannelHandshake(handshake: String) =
        secureChannelService.sendHandshake(handshake)

    fun cancelOrder(orderId: String): Completable =
        cancelOrderUseCase.invoke(orderId)

    fun shouldShowEntitySwitchSilverKycUpsell(): Single<Boolean> =
        userIdentity.getHighestApprovedKycTier().map { tier ->
            val showUpsell =
                tier != Tier.GOLD && !onboardingPrefs.isEntitySwitchSilverKycUpsellDismissed
            if (showUpsell) onboardingPrefs.isEntitySwitchSilverKycUpsellDismissed = true
            showUpsell
        }

    fun processDeepLinkV2(url: Uri): Completable =
        deeplinkRedirector.processDeeplinkURL(url)

    fun clearDeepLink(): Completable =
        Completable.fromAction {
            deepLinkPersistence.popDataFromSharedPrefs()
        }
}
