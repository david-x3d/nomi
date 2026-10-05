package com.nomi.app.ui.app

import com.nomi.app.ai.model.MenuDish
import com.nomi.app.data.preferences.ProviderPipeline
import com.nomi.app.ui.capture.MenuScanUiState
import com.nomi.app.ui.capture.menuDishKey
import com.nomi.app.ui.capture.mergeMenuDishes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Owns menu pagination and selection before handing a selected meal to logging. */
internal class MenuScanController(
    private val providers: AiProviderAccess,
    private val scope: CoroutineScope,
    private val inUserLanguage: (String) -> String,
    private val onSelected: (List<MenuDish>, String) -> Unit,
) {
    private var menuJob: Job? = null
    private val mutableMenuScanState = MutableStateFlow(MenuScanUiState())
    val menuScanState = mutableMenuScanState.asStateFlow()
    private var menuScanRequestId = 0L
    fun beginMenuScan() {
        menuScanRequestId += 1
        menuJob?.cancel()
        mutableMenuScanState.value = MenuScanUiState()
    }

    fun updateMenuSearch(query: String) {
        mutableMenuScanState.value = mutableMenuScanState.value.copy(query = query.take(200))
    }

    fun scanMenuPage(bytes: ByteArray, mediaType: String) {
        if (bytes.isEmpty()) return
        menuJob?.cancel()
        val requestId = ++menuScanRequestId
        val before = mutableMenuScanState.value
        mutableMenuScanState.value = before.copy(isProcessing = true, errorMessage = null)
        val job = scope.launch {
            runCatching {
                providers.withProvider(ProviderPipeline.VISION) { it.scanMenu(bytes, mediaType) }
            }.onSuccess { result ->
                if (requestId != menuScanRequestId) return@onSuccess
                val current = mutableMenuScanState.value
                mutableMenuScanState.value = current.copy(
                    restaurantName = current.restaurantName
                        ?: result.restaurantName?.trim()?.takeIf(String::isNotBlank),
                    items = mergeMenuDishes(current.items, result.items),
                    pageCount = current.pageCount + 1,
                    isProcessing = false,
                    errorMessage = null,
                    notes = (current.notes + result.notes).distinct().takeLast(20),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (requestId != menuScanRequestId) return@onFailure
                mutableMenuScanState.value = mutableMenuScanState.value.copy(
                    isProcessing = false,
                    errorMessage = inUserLanguage("Nomi couldn't read that menu page. Add a clearer photo."),
                )
            }
        }
        menuJob = job
        job.invokeOnCompletion { bytes.fill(0) }
    }

    fun toggleMenuDish(dish: MenuDish) {
        val key = menuDishKey(dish)
        val selected = mutableMenuScanState.value.selectedDishKeys
        mutableMenuScanState.value = mutableMenuScanState.value.copy(
            selectedDishKeys = if (key in selected) selected - key else selected + key,
        )
    }

    fun selectMenuDishes() {
        val state = mutableMenuScanState.value
        val dishes = state.items.filter { menuDishKey(it) in state.selectedDishKeys }
        if (dishes.isEmpty()) return
        val restaurant = state.restaurantName
        val text = buildString {
            restaurant?.takeIf(String::isNotBlank)?.let { append("At ").append(it.trim()).append(": ") }
            dishes.forEachIndexed { index, dish ->
                if (index > 0) append("; ")
                append("1 serving ").append(dish.name.trim())
                dish.number?.takeIf(String::isNotBlank)?.let {
                    append(" (menu number ").append(it.trim()).append(')')
                }
                dish.description?.takeIf(String::isNotBlank)?.let {
                    append(". Menu description: ").append(it.trim())
                }
                dish.quantityText?.takeIf(String::isNotBlank)?.let {
                    append(". Printed serving: ").append(it.trim())
                }
            }
        }.take(MAX_MENU_LOGGING_TEXT_CHARS)
        onSelected(dishes, text)
    }

}

private const val MAX_MENU_LOGGING_TEXT_CHARS = 1_500
