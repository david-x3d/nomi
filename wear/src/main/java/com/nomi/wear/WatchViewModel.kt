package com.nomi.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TodayState {
    data object Loading : TodayState
    /** The phone has never published to this watch, or Play services is unavailable. */
    data object NotConnected : TodayState
    data class Ready(val today: WatchToday) : TodayState
}

/** Where the last logging request stands. */
sealed interface LogStatus {
    data object Idle : LogStatus
    /** Sent; the phone is looking the food up and saving it. */
    data object Waiting : LogStatus
    data object PhoneUnreachable : LogStatus
    data class Done(val result: LogResult) : LogStatus
}

class WatchViewModel(application: Application) : AndroidViewModel(application) {
    private val phone = PhoneConnection(application)

    val today: StateFlow<TodayState> = phone.today()
        .map { today -> today?.let { TodayState.Ready(it.asOf(LocalDate.now())) } ?: TodayState.NotConnected }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState.Loading)

    private val mutableStatus = MutableStateFlow<LogStatus>(LogStatus.Idle)
    val status: StateFlow<LogStatus> = mutableStatus.asStateFlow()

    private var waitTimeout: Job? = null

    private val mutableVoiceRequested = MutableStateFlow(false)
    /** Set when the tile asked to start dictating; cleared once the input has been opened. */
    val voiceRequested: StateFlow<Boolean> = mutableVoiceRequested.asStateFlow()

    fun requestVoiceInput() {
        mutableVoiceRequested.value = true
    }

    fun consumeVoiceRequest() {
        mutableVoiceRequested.value = false
    }

    init {
        viewModelScope.launch { phone.requestSync() }
        viewModelScope.launch {
            phone.results().collect { result ->
                waitTimeout?.cancel()
                mutableStatus.value = LogStatus.Done(result)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { phone.requestSync() }
    }

    fun logText(text: String) = send { phone.logText(text) }

    fun logQuick(item: QuickItem) = send { phone.logQuick(item) }

    fun dismissStatus() {
        waitTimeout?.cancel()
        mutableStatus.value = LogStatus.Idle
    }

    private fun send(request: suspend () -> Boolean) {
        viewModelScope.launch {
            mutableStatus.value = LogStatus.Waiting
            if (!request()) {
                mutableStatus.value = LogStatus.PhoneUnreachable
                return@launch
            }
            // The phone always answers, but an answer can be lost when the watch drops out of
            // reach. The new total still arrives with the next data item, so stop waiting.
            waitTimeout?.cancel()
            waitTimeout = launch {
                delay(RESULT_TIMEOUT_MILLIS)
                if (mutableStatus.value == LogStatus.Waiting) mutableStatus.value = LogStatus.Idle
            }
        }
    }

    private companion object {
        const val RESULT_TIMEOUT_MILLIS = 170_000L
    }
}
