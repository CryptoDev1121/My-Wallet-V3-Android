package piuk.blockchain.android.ui.home

import com.blockchain.core.chains.bitcoincash.BchDataManager
import com.blockchain.metadata.MetadataService
import com.blockchain.nabu.datamanagers.NabuDataManager
import com.blockchain.notifications.NotificationTokenManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import piuk.blockchain.android.util.AppUtil
import piuk.blockchain.androidcore.data.ethereum.EthDataManager
import piuk.blockchain.androidcore.data.walletoptions.WalletOptionsState
import piuk.blockchain.androidcore.utils.extensions.then
import timber.log.Timber

class CredentialsWiper(
    private val ethDataManager: EthDataManager,
    private val appUtil: AppUtil,
    private val notificationTokenManager: NotificationTokenManager,
    private val bchDataManager: BchDataManager,
    private val metadataService: MetadataService,
    private val nabuDataManager: NabuDataManager,
    private val walletOptionsState: WalletOptionsState
) {
    fun wipe() {
        notificationTokenManager.revokeAccessToken().then {
            Completable.fromAction {
                appUtil.unpairWallet()
                ethDataManager.clearAccountDetails()
                bchDataManager.clearAccountDetails()
                nabuDataManager.clearAccessToken()
                metadataService.reset()
                walletOptionsState.wipe()
            }
        }
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onError = {
                    Timber.e(it)
                },
                onComplete = {
                    appUtil.logout()
                    appUtil.restartApp()
                }
            )
    }
}
