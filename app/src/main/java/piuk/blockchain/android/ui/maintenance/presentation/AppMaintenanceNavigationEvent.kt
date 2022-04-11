package piuk.blockchain.android.ui.maintenance.presentation

import com.blockchain.commonarch.presentation.mvi_v2.NavigationEvent

sealed interface AppMaintenanceNavigationEvent : NavigationEvent {
    data class OpenUrl(val url: String) : AppMaintenanceNavigationEvent

    /**
     * Trigger play store download
     */
    object LaunchAppUpdate : AppMaintenanceNavigationEvent

    /**
     * Resume from wherever the app was suspended to show the maintenance screen
     */
    object ResumeAppFlow : AppMaintenanceNavigationEvent
}