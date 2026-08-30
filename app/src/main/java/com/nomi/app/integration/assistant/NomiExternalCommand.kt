package com.nomi.app.integration.assistant

sealed interface NomiExternalCommand {
    data class LogFood(val text: String) : NomiExternalCommand
    data object CapturePhoto : NomiExternalCommand
    data object ScanMenu : NomiExternalCommand
    data object SpeakCalories : NomiExternalCommand
}
