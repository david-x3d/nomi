package com.nomi.app.ui.app

import com.nomi.app.ui.capture.BarcodeAmountUiState
import com.nomi.app.ui.logging.FoodLoggingUiState
import kotlinx.coroutines.flow.MutableStateFlow

/** The editable draft is distinct from the immutable request or save currently using it. */
internal class LoggingDraft {
    val mutableLoggingState = MutableStateFlow<FoodLoggingUiState>(FoodLoggingUiState.Input())
    val mutableBarcodeAmountState = MutableStateFlow<BarcodeAmountUiState?>(null)
    val mutableEditedEntryId = MutableStateFlow<Long?>(null)
    var lastLoggingText = ""
    var destination: LogDestination? = null
}
