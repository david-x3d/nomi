package com.nomi.app.ui.today

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Caret, keyboard dismissal and the scroll position belong to the same composing session. */
internal class TodayComposerState(
    private val closeKeyboard: () -> Unit,
    isOpen: Boolean = false,
    caret: Int = 0,
) {
    var isOpen by mutableStateOf(isOpen)
    var caret by mutableStateOf(caret)
    var previousPosition: Pair<Int, Int>? = null

    fun close() {
        isOpen = false
        closeKeyboard()
    }

    fun openCapture() {
        previousPosition = null
        close()
    }
}

@Composable
internal fun rememberTodayComposerState(list: LazyListState): TodayComposerState {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val closeKeyboard by rememberUpdatedState<() -> Unit> {
        keyboard?.hide()
        focus.clearFocus(force = true)
    }
    val state = rememberSaveable(
        saver = listSaver<TodayComposerState, Any>(
            save = { listOf(it.isOpen, it.caret) },
            restore = { TodayComposerState({ closeKeyboard() }, it[0] as Boolean, it[1] as Int) },
        ),
    ) { TodayComposerState({ closeKeyboard() }) }
    LaunchedEffect(state.isOpen) {
        if (state.isOpen) {
            if (state.previousPosition == null) {
                state.previousPosition = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
            }
        } else {
            state.previousPosition?.let { (index, offset) ->
                state.previousPosition = null
                list.scrollToItem(index, offset)
            }
        }
    }
    return state
}
