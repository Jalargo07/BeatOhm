package com.musicdownloader.ui

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.musicdownloader.R

object TutorialManager {

    private const val PREFS = "tutorial_prefs"
    private const val KEY_PREFIX = "shown_"

    data class TooltipStep(
        val targetView: () -> View?,
        val title: String,
        val message: String
    )

    fun isSectionShown(context: Context, section: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("$KEY_PREFIX$section", false)
    }

    fun markSectionShown(context: Context, section: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean("$KEY_PREFIX$section", true) }
    }

    fun showTutorial(
        activity: Activity,
        section: String,
        steps: List<TooltipStep>,
        onFinished: (() -> Unit)? = null
    ) {
        if (isSectionShown(activity, section)) {
            onFinished?.invoke()
            return
        }
        if (steps.isEmpty()) {
            markSectionShown(activity, section)
            onFinished?.invoke()
            return
        }
        showStep(activity, steps, 0, section, onFinished)
    }

    private fun showStep(
        activity: Activity,
        steps: List<TooltipStep>,
        index: Int,
        section: String,
        onFinished: (() -> Unit)?
    ) {
        if (index >= steps.size) {
            markSectionShown(activity, section)
            onFinished?.invoke()
            return
        }

        val step = steps[index]
        val target = step.targetView() ?: run {
            showStep(activity, steps, index + 1, section, onFinished)
            return
        }

        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_tooltip, null)
        dialogView.findViewById<TextView>(R.id.tv_tooltip_title).text = step.title
        dialogView.findViewById<TextView>(R.id.tv_tooltip_message).text = step.message

        val remaining = steps.size - index - 1
        val btnSkip = dialogView.findViewById<TextView>(R.id.btn_tooltip_skip)
        val btnNext = dialogView.findViewById<TextView>(R.id.btn_tooltip_next)

        if (remaining > 0) {
            btnSkip.visibility = View.VISIBLE
            btnSkip.text = activity.getString(R.string.tutorial_skip)
        } else {
            btnSkip.visibility = View.GONE
        }

        btnNext.text = activity.getString(R.string.tutorial_got_it)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnSkip.setOnClickListener {
            dialog.dismiss()
            markSectionShown(activity, section)
            onFinished?.invoke()
        }

        btnNext.setOnClickListener {
            dialog.dismiss()
            showStep(activity, steps, index + 1, section, onFinished)
        }

        dialog.show()

        highlightView(target)
    }

    private fun highlightView(view: View) {
        view.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(300)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    fun resetAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { clear() }
    }
}
