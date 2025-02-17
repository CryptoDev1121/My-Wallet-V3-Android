package piuk.blockchain.android.ui.interest.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.blockchain.commonarch.presentation.mvi_v2.MVIFragment
import com.blockchain.commonarch.presentation.mvi_v2.ModelConfigArgs
import com.blockchain.commonarch.presentation.mvi_v2.NavigationRouter
import com.blockchain.commonarch.presentation.mvi_v2.bindViewModel
import com.blockchain.koin.payloadScope
import info.blockchain.balance.AssetInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ViewModelOwner
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.scope.getViewModel
import piuk.blockchain.android.ui.interest.presentation.composables.InterestDashboardScreen

class InterestDashboardFragment :
    MVIFragment<InterestDashboardViewState>() {

    private val viewModel: InterestDashboardViewModel by lazy {
        payloadScope.getViewModel(owner = { ViewModelOwner.from(this) })
    }
    private val sharedViewModel: InterestDashboardSharedViewModel by sharedViewModel()

    private val navigationRouter: NavigationRouter<InterestDashboardNavigationEvent> by lazy {
        activity as? NavigationRouter<InterestDashboardNavigationEvent>
            ?: error("host does not implement NavigationRouter<InterestDashboardNavigationEvent>")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ScreenContent()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViewModel()
        loadDashboard()
    }

    private fun setupViewModel() {
        bindViewModel(viewModel = viewModel, navigator = navigationRouter, args = ModelConfigArgs.NoArgs)

        lifecycleScope.launch {
            sharedViewModel.refreshBalancesFlow.flowWithLifecycle(lifecycle).collect {
                loadDashboard()
            }
        }
    }

    @Composable
    private fun ScreenContent() {
        val state = viewModel.viewState.collectAsState()

        InterestDashboardScreen(
            viewState = state.value,
            loadDashboard = ::loadDashboard,
            startKyc = ::startKyc,
            interestItemClicked = ::interestItemClicked,
            filterData = ::filterData
        )
    }

    override fun onStateUpdated(state: InterestDashboardViewState) {
    }

    private fun interestItemClicked(cryptoCurrency: AssetInfo, hasBalance: Boolean) {
        viewModel.onIntent(
            InterestDashboardIntents.InterestItemClicked(cryptoCurrency = cryptoCurrency, hasBalance = hasBalance)
        )
    }

    private fun loadDashboard() {
        viewModel.onIntent(InterestDashboardIntents.LoadDashboard)
    }

    private fun filterData(filter: String) {
        viewModel.onIntent(InterestDashboardIntents.FilterData(filter))
    }

    private fun startKyc() {
        viewModel.onIntent(InterestDashboardIntents.StartKyc)
    }

    companion object {
        fun newInstance(): InterestDashboardFragment = InterestDashboardFragment()
    }
}
