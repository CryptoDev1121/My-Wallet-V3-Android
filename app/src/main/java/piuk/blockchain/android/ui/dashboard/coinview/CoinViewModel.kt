package piuk.blockchain.android.ui.dashboard.coinview

import com.blockchain.charts.ChartEntry
import com.blockchain.coincore.CryptoAsset
import com.blockchain.commonarch.presentation.mvi.MviModel
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.enviroment.EnvironmentConfig
import com.blockchain.logging.RemoteLogger
import com.blockchain.nabu.BlockedReason
import com.blockchain.nabu.FeatureAccess
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import timber.log.Timber

class CoinViewModel(
    initialState: CoinViewState,
    mainScheduler: Scheduler,
    private val interactor: CoinViewInteractor,
    environmentConfig: EnvironmentConfig,
    private val remoteLogger: RemoteLogger
) : MviModel<CoinViewState, CoinViewIntent>(
    initialState,
    mainScheduler,
    environmentConfig,
    remoteLogger
) {
    override fun performAction(previousState: CoinViewState, intent: CoinViewIntent): Disposable? =
        when (intent) {
            is CoinViewIntent.LoadAsset -> {
                interactor.loadAssetDetails(intent.assetTicker).subscribeBy(
                    onSuccess = { (asset, fiatCurrency) ->
                        asset?.let {
                            process(CoinViewIntent.AssetLoaded(it, fiatCurrency))
                            process(CoinViewIntent.LoadAccounts(it))
                            process(CoinViewIntent.LoadRecurringBuys(it.assetInfo))
                            process(CoinViewIntent.LoadAssetDetails(it.assetInfo))
                        } ?: process(CoinViewIntent.UpdateErrorState(CoinViewError.UnknownAsset))
                    },
                    onError = {
                        process(CoinViewIntent.UpdateErrorState(CoinViewError.UnknownAsset))
                    }
                )
            }
            is CoinViewIntent.LoadAccounts -> loadAccounts(intent)
            is CoinViewIntent.UpdateAccountDetails -> {
                previousState.selectedFiat?.let {
                    process(CoinViewIntent.LoadAssetChart(intent.asset, intent.assetInformation.prices, it))
                } ?: process(CoinViewIntent.UpdateErrorState(CoinViewError.MissingSelectedFiat))
                null
            }
            is CoinViewIntent.LoadAssetChart -> loadChart(intent)
            is CoinViewIntent.LoadNewChartPeriod -> {
                previousState.asset?.let {
                    loadNewTimePeriod(it, intent, previousState)
                } ?: process(CoinViewIntent.UpdateErrorState(CoinViewError.UnknownAsset))
                null
            }
            is CoinViewIntent.LoadRecurringBuys -> loadRecurringBuys(intent)
            is CoinViewIntent.LoadQuickActions -> loadQuickActions(intent)
            is CoinViewIntent.ToggleWatchlist -> toggleWatchlist(previousState)
            is CoinViewIntent.CheckScreenToOpen -> getAccountActions(intent)
            is CoinViewIntent.CheckBuyStatus -> checkUserBuyStatus()
            is CoinViewIntent.LoadAssetDetails -> loadAssetInfoDetails(intent)
            CoinViewIntent.ResetErrorState,
            CoinViewIntent.ResetViewState,
            is CoinViewIntent.UpdateWatchlistState,
            CoinViewIntent.BuyHasWarning,
            is CoinViewIntent.UpdateErrorState,
            is CoinViewIntent.UpdateViewState,
            is CoinViewIntent.AssetLoaded -> null
        }

    private fun toggleWatchlist(previousState: CoinViewState) =
        previousState.asset?.assetInfo?.let {
            if (previousState.isAddedToWatchlist) {
                interactor.removeFromWatchlist(it)
                    .subscribeBy(
                        onComplete = {
                            process(CoinViewIntent.UpdateWatchlistState(isAddedToWatchlist = false))
                            process(
                                CoinViewIntent.UpdateViewState(
                                    CoinViewViewState.UpdatedWatchlist(addedToWatchlist = false)
                                )
                            )
                        },
                        onError = {
                            process(CoinViewIntent.UpdateErrorState(CoinViewError.WatchlistUpdateFailed))
                        }
                    )
            } else {
                interactor.addToWatchlist(it)
                    .subscribeBy(
                        onSuccess = {
                            process(CoinViewIntent.UpdateWatchlistState(isAddedToWatchlist = true))
                            process(
                                CoinViewIntent.UpdateViewState(
                                    CoinViewViewState.UpdatedWatchlist(addedToWatchlist = true)
                                )
                            )
                        },
                        onError = {
                            process(CoinViewIntent.UpdateErrorState(CoinViewError.WatchlistUpdateFailed))
                        }
                    )
            }
        }

    private fun loadAssetInfoDetails(intent: CoinViewIntent.LoadAssetDetails) =
        interactor.loadAssetInformation(intent.asset).subscribeBy(
            onSuccess = {
                process(CoinViewIntent.UpdateViewState(CoinViewViewState.ShowAssetDetails(it)))
            },
            onError = {
                process(CoinViewIntent.UpdateErrorState(CoinViewError.AssetDetailsLoadError))
            }
        )

    private fun getAccountActions(intent: CoinViewIntent.CheckScreenToOpen) =
        interactor.getAccountActions(intent.cryptoAccountSelected.account)
            .subscribeBy(
                onSuccess = { screenToNavigate ->
                    process(CoinViewIntent.UpdateViewState(screenToNavigate))
                },
                onError = {
                    Timber.e("***> Error Loading account actions: $it")
                    process(CoinViewIntent.UpdateErrorState(CoinViewError.ActionsLoadError))
                }
            )

    private fun checkUserBuyStatus() = interactor.checkIfUserCanBuy().subscribeBy(
        onSuccess = {
            if ((it as? FeatureAccess.Blocked)?.reason is BlockedReason.TooManyInFlightTransactions) {
                process(CoinViewIntent.BuyHasWarning)
            }
        },
        onError = {
            remoteLogger.logException(it, "CoinViewModel userCanBuy failed")
        }
    )

    private fun loadRecurringBuys(intent: CoinViewIntent.LoadRecurringBuys) =
        interactor.loadRecurringBuys(intent.asset)
            .subscribeBy(
                onSuccess = {
                    process(
                        CoinViewIntent.UpdateViewState(
                            CoinViewViewState.ShowRecurringBuys(recurringBuys = it.first, shouldShowUpsell = it.second)
                        )
                    )
                },
                onError = {
                    process(CoinViewIntent.UpdateErrorState(CoinViewError.RecurringBuysLoadError))
                }
            )

    private fun loadQuickActions(intent: CoinViewIntent.LoadQuickActions) =
        interactor.loadQuickActions(intent.totalCryptoBalance, intent.accountList, intent.asset)
            .subscribeBy(
                onSuccess = { actions ->
                    process(
                        CoinViewIntent.UpdateViewState(
                            CoinViewViewState.QuickActionsLoaded(
                                startAction = actions.startAction,
                                endAction = actions.endAction,
                                actionableAccount = actions.actionableAccount
                            )
                        )
                    )
                },
                onError = {
                    process(CoinViewIntent.UpdateErrorState(CoinViewError.QuickActionsFailed))
                }
            )

    private fun loadNewTimePeriod(
        it: CryptoAsset,
        intent: CoinViewIntent.LoadNewChartPeriod,
        previousState: CoinViewState
    ) = interactor.loadHistoricPrices(it, intent.timePeriod)
        .subscribeBy(
            onSuccess = { historicalRates ->
                when {
                    previousState.assetPrices == null -> process(
                        CoinViewIntent.UpdateErrorState(CoinViewError.MissingAssetPrices)
                    )
                    previousState.selectedFiat == null -> process(
                        CoinViewIntent.UpdateErrorState(CoinViewError.MissingSelectedFiat)
                    )
                    else -> {
                        if (historicalRates.isEmpty()) {
                            process(CoinViewIntent.UpdateErrorState(CoinViewError.ChartLoadError))
                        } else {
                            process(
                                CoinViewIntent.UpdateViewState(
                                    CoinViewViewState.ShowAssetInfo(
                                        entries = historicalRates.map { point ->
                                            ChartEntry(
                                                point.timestamp.toFloat(),
                                                point.rate.toFloat()
                                            )
                                        },
                                        prices = previousState.assetPrices,
                                        historicalRateList = historicalRates,
                                        selectedFiat = previousState.selectedFiat
                                    )
                                )
                            )
                        }
                    }
                }
            },
            onError = {
                process(CoinViewIntent.UpdateErrorState(CoinViewError.ChartLoadError))
            }
        )

    private fun loadChart(intent: CoinViewIntent.LoadAssetChart) =
        interactor.loadHistoricPrices(intent.asset, HistoricalTimeSpan.DAY)
            .subscribeBy(
                onSuccess = { list ->
                    if (list.isEmpty()) {
                        process(CoinViewIntent.UpdateErrorState(CoinViewError.ChartLoadError))
                    } else {
                        process(
                            CoinViewIntent.UpdateViewState(
                                CoinViewViewState.ShowAssetInfo(
                                    entries = list.map { point ->
                                        ChartEntry(
                                            point.timestamp.toFloat(),
                                            point.rate.toFloat()
                                        )
                                    },
                                    prices = intent.assetPrice,
                                    historicalRateList = list,
                                    selectedFiat = intent.selectedFiat
                                )
                            )
                        )
                    }
                },
                onError = {
                    process(CoinViewIntent.UpdateErrorState(CoinViewError.ChartLoadError))
                }
            )

    private fun loadAccounts(intent: CoinViewIntent.LoadAccounts) =
        interactor.loadAccountDetails(intent.asset)
            .subscribeBy(
                onSuccess = { accountInfo ->
                    process(
                        CoinViewIntent.UpdateAccountDetails(
                            viewState = when (accountInfo) {
                                is AssetInformation.AccountsInfo -> CoinViewViewState.ShowAccountInfo(
                                    accountInfo, accountInfo.isAddedToWatchlist
                                )
                                is AssetInformation.NonTradeable -> CoinViewViewState.ShowNonTradeableAccount(
                                    accountInfo.isAddedToWatchlist
                                )
                            },
                            assetInformation = accountInfo,
                            asset = intent.asset,
                            isAddedToWatchlist = accountInfo.isAddedToWatchlist
                        )
                    )

                    if (accountInfo is AssetInformation.AccountsInfo) {
                        process(
                            CoinViewIntent.LoadQuickActions(
                                accountInfo.totalCryptoBalance, accountInfo.accountsList.map { it.account },
                                intent.asset
                            )
                        )
                    }
                },
                onError = {
                    process(CoinViewIntent.UpdateErrorState(CoinViewError.WalletLoadError))
                }
            )
}
