package com.blockchain.commonarch.presentation.base

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.blockchain.analytics.Analytics
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.koin.android.ext.android.inject

abstract class ComposeModalBottomDialog : BottomSheetDialogFragment() {

    interface Host : HostedBottomSheet.Host

    protected open val host: HostedBottomSheet.Host by lazy {
        parentFragment as? HostedBottomSheet.Host
            ?: activity as? HostedBottomSheet.Host
            ?: throw IllegalStateException("Host is not a SlidingModalBottomDialog.Host")
    }

    private var dismissed = false

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>

    protected val analytics: Analytics by inject()

    final override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireActivity())

        dialog.setContentView(
            ComposeView(requireContext()).apply {
                setContent {
                    Sheet()
                }
            }
        )

        dialog.setCanceledOnTouchOutside(false)

        val layout =
            dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) as FrameLayout
        bottomSheetBehavior = BottomSheetBehavior.from(layout)

        bottomSheetBehavior.addBottomSheetCallback(object :
                BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(view: View, i: Int) {
                    when (i) {
                        BottomSheetBehavior.STATE_EXPANDED -> onSheetExpanded()
                        BottomSheetBehavior.STATE_COLLAPSED -> onSheetCollapsed()
                        BottomSheetBehavior.STATE_HIDDEN -> onSheetHidden()
                        else -> { /* shouldn't get here! */
                        }
                    }
                }

                override fun onSlide(view: View, v: Float) {}
            })

        dialog.setOnShowListener {
            bottomSheetBehavior.skipCollapsed = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        return dialog
    }

    @Composable
    abstract fun Sheet()

    @CallSuper
    protected open fun onSheetHidden() {
        dismiss()
    }

    @CallSuper
    protected open fun onSheetExpanded() {
    }

    @CallSuper
    protected open fun onSheetCollapsed() {
    }

    // We use this dismissed flag to make sure that only one of onCancel or dismiss methods are called,
    // when the bottomsheet is dismissed in different ways.
    // When the bottomsheet is dismissed with the back button onCancel is called but not dismiss. On the hand
    // if bottomsheet is dismissed by code (.dismiss()) or with slide gesture then both methods are called.

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        if (dismissed) {
            return
        }
        dismissed = true
        host.onSheetClosed(this)
        resetSheetParent()
    }

    override fun dismiss() {
        super.dismiss()
        if (dismissed) {
            return
        }
        dismissed = true
        resetSheetParent()
        host.onSheetClosed(this)
    }

    private fun resetSheetParent() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}
