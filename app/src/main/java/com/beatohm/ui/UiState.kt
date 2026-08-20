package com.beatohm.ui

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * Sealed class that represents the common UI states for list screens:
 * Loading, Empty, Error, and Content.
 *
 * Usage in Fragment:
 * ```
 * uiState.collectLatest { state ->
 *     when (state) {
 *         is UiState.Loading -> showLoading()
 *         is UiState.Empty -> showEmpty()
 *         is UiState.Error -> showError(state.message)
 *         is UiState.Content -> showContent(state.data)
 *     }
 * }
 * ```
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Empty(val messageRes: Int = 0) : UiState<Nothing>()
    data class Error(val message: String, val retryAction: (() -> Unit)? = null) : UiState<Nothing>()
    data class Content<T>(val data: T) : UiState<T>()

    val isLoading get() = this is Loading
    val isEmpty get() = this is Empty
    val isError get() = this is Error
    val isContent get() = this is Content

    fun dataOrNull(): T? = (this as? Content)?.data
}

/**
 * Helper extension to bind common UI states to standard views.
 * Reduces boilerplate in fragments that show list + empty + error + loading.
 */
object UiStateHelper {

    /**
     * Binds UI state to a RecyclerView, empty view, error view, and progress bar.
     * Call this from your fragment's state collection.
     *
     * @param state current UiState
     * @param recyclerView the list to show/hide
     * @param emptyView the empty state container (shown when list is empty)
     * @param errorView the error text view (optional)
     * @param progressBar the loading indicator (optional)
     * @param retryButton the retry button (optional)
     */
    fun <T> bind(
        state: UiState<T>,
        recyclerView: RecyclerView,
        emptyView: View? = null,
        errorView: TextView? = null,
        progressBar: ProgressBar? = null,
        retryButton: MaterialButton? = null
    ) {
        recyclerView.isVisible = state is UiState.Content
        emptyView?.isVisible = state is UiState.Empty
        progressBar?.isVisible = state is UiState.Loading
        errorView?.isVisible = state is UiState.Error

        when (state) {
            is UiState.Empty -> {
                if (state.messageRes != 0) {
                    (emptyView as? TextView)?.setText(state.messageRes)
                }
            }
            is UiState.Error -> {
                errorView?.text = state.message
                retryButton?.isVisible = state.retryAction != null
                retryButton?.setOnClickListener { state.retryAction?.invoke() }
            }
            else -> { /* nothing extra */ }
        }
    }
}
