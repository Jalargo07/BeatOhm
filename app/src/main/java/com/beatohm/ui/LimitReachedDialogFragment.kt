package com.beatohm.ui

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.beatohm.ads.InMobiManager
import com.beatohm.R

class LimitReachedDialogFragment(
    private val onRewardEarned: () -> Unit
) : DialogFragment() {

    private var statusView: TextView? = null
    private var watchAdButton: com.google.android.material.button.MaterialButton? = null
    private var retryButton: com.google.android.material.button.MaterialButton? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())

        val view = requireActivity().layoutInflater.inflate(
            R.layout.dialog_limit_reached, null
        )

        builder.setView(view)

        statusView = view.findViewById(R.id.tv_limit_status)
        watchAdButton = view.findViewById(R.id.btn_limit_watch_ad)
        retryButton = view.findViewById(R.id.btn_limit_retry)

        view.findViewById<TextView>(R.id.btn_limit_cancel).setOnClickListener {
            dismiss()
        }

        watchAdButton?.setOnClickListener { attemptReward() }
        retryButton?.setOnClickListener { attemptReward() }

        return requireDialog()
    }

    private fun attemptReward() {
        statusView?.visibility = View.GONE
        watchAdButton?.visibility = View.VISIBLE
        retryButton?.visibility = View.GONE

        InMobiManager.showRewardedAd(
            requireActivity(),
            onRewardEarned = {
                onRewardEarned()
                dismiss()
            },
            onFailed = { _ ->
                showUnavailableState()
            }
        )
    }

    private fun showUnavailableState() {
        statusView?.visibility = View.VISIBLE
        watchAdButton?.visibility = View.GONE
        retryButton?.visibility = View.VISIBLE
    }

    companion object {
        const val TAG = "LimitReachedDialog"

        fun newInstance(onRewardEarned: () -> Unit): LimitReachedDialogFragment {
            return LimitReachedDialogFragment(onRewardEarned)
        }
    }
}
