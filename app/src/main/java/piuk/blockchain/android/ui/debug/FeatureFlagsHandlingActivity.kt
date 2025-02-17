package piuk.blockchain.android.ui.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import com.blockchain.commonarch.presentation.base.BlockchainActivity
import com.blockchain.componentlib.alert.BlockchainSnackbar
import com.blockchain.componentlib.demo.ComponentLibDemoActivity
import com.blockchain.koin.scopedInject
import com.blockchain.logging.RemoteLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.google.android.material.snackbar.Snackbar
import info.blockchain.balance.FiatCurrency
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.koin.android.ext.android.inject
import piuk.blockchain.android.databinding.ActivityLocalFeatureFlagsBinding
import piuk.blockchain.android.ui.dashboard.announcements.AnnouncementList
import piuk.blockchain.android.ui.dashboard.announcements.DismissRecorder
import piuk.blockchain.android.ui.referral.domain.model.ReferralData
import piuk.blockchain.android.ui.referral.presentation.ReferralSheet
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.access.PinRepository
import piuk.blockchain.androidcore.utils.PersistentPrefs

// todo (othman): revert this back to AppCompatActivity once referrals api is implemented
// PR - https://github.com/blockchain/wallet-android-private/pull/3266/
// jira - https://blockchain.atlassian.net/browse/AND-6144
class FeatureFlagsHandlingActivity : BlockchainActivity() {

    private lateinit var binding: ActivityLocalFeatureFlagsBinding
    private val featureFlagHandler: FeatureFlagHandler by inject()
    private val compositeDisposable = CompositeDisposable()
    private val prefs: PersistentPrefs by inject()
    private val appUtils: AppUtil by inject()
    private val loginState: PinRepository by inject()
    private val remoteLogger: RemoteLogger by inject()
    private val simpleBuyPrefs: SimpleBuyPrefs by inject()
    private val currencyPrefs: CurrencyPrefs by inject()

    private val featuresAdapter: FeatureFlagAdapter = FeatureFlagAdapter()

    override val alwaysDisableScreenshots = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocalFeatureFlagsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        featuresAdapter.items = featureFlagHandler.getAllFeatureFlags().entries.map { (featureFlag, status) ->
            FeatureFlagItem(
                name = featureFlag.readableName,
                featureFlagState = status,
                onStatusChanged = { featureStatus ->
                    featureFlagHandler.setFeatureFlagState(featureFlag, featureStatus)
                }
            )
        }

        with(binding) {
            featureFlagList.apply {
                layoutManager = LinearLayoutManager(
                    this@FeatureFlagsHandlingActivity,
                    LinearLayoutManager.VERTICAL,
                    false
                )
                adapter = featuresAdapter
            }
            val parent = nestedParent
            btnRndDeviceId.setOnClickListener { onRndDeviceId() }
            btnResetWallet.setOnClickListener { onResetWallet() }
            btnResetAnnounce.setOnClickListener { onResetAnnounce() }
            btnResetPrefs.setOnClickListener { onResetPrefs() }
            showReferral.setOnClickListener { showReferral() }
            clearSimpleBuyState.setOnClickListener { clearSimpleBuyState() }
            btnStoreLinkId.setOnClickListener { prefs.pitToWalletLinkId = "11111111-2222-3333-4444-55556666677" }
            btnComponentLib.setOnClickListener { onComponentLib() }
            deviceCurrency.text = "Select a new currency. Current one is ${currencyPrefs.selectedFiatCurrency}"
            firebaseToken.text = prefs.firebaseToken

            radioEur.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currencyPrefs.selectedFiatCurrency = FiatCurrency.fromCurrencyCode("EUR")
                    showSnackbar("Currency changed to EUR")
                }
            }

            radioUsd.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currencyPrefs.selectedFiatCurrency = FiatCurrency.fromCurrencyCode("USD")
                    showSnackbar("Currency changed to USD")
                }
            }

            radioGbp.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    currencyPrefs.selectedFiatCurrency = FiatCurrency.fromCurrencyCode("GBP")
                    showSnackbar("Currency changed to GBP")
                }
            }
        }
    }

    private fun showSnackbar(text: String) {
        BlockchainSnackbar.make(
            binding.root,
            text,
            duration = Snackbar.LENGTH_SHORT,
        ).show()
    }

    private fun showReferral() {
        showBottomSheet(
            ReferralSheet.newInstance(
                ReferralData(
                    rewardTitle = "Invite friends, get $30.00!",
                    rewardSubtitle = "Increase your earnings on each successful invite",
                    code = "DIEG4321",
                    criteria = listOf("Sign up using your code", "Verify their identity", "Trade (min 50)")
                )
            )
        )
    }

    private fun clearSimpleBuyState() {
        simpleBuyPrefs.clearBuyState()
        showSnackbar("Local SB State cleared")
    }

    private fun onRndDeviceId() {
        prefs.qaRandomiseDeviceId = true
        showSnackbar("Device ID randomisation enabled")
    }

    private fun onResetWallet() {
        appUtils.clearCredentialsAndRestart()
        showSnackbar("Wallet reset")
    }

    private fun onResetAnnounce() {
        val announcementList: AnnouncementList by scopedInject()
        val dismissRecorder: DismissRecorder by scopedInject()

        dismissRecorder.undismissAll(announcementList)

        showSnackbar("Announcement reset")
    }

    private fun onResetPrefs() {
        prefs.clear()

        remoteLogger.logEvent("debug clear prefs. Pin reset")
        loginState.clearPin()

        showSnackbar("Prefs Reset")
    }

    private fun onComponentLib() {
        startActivity(Intent(this, ComponentLibDemoActivity::class.java))
    }

    override fun onPause() {
        compositeDisposable.clear()
        super.onPause()
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, FeatureFlagsHandlingActivity::class.java)
    }
}
