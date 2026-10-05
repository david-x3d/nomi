package com.nomi.app.ui.today

import java.time.LocalDate

/** UI projection of the deletion controller; database snapshots stay with that controller. */
data class PendingFoodDeletion(val entry: TodayFoodEntry, val date: LocalDate, val isRestoring: Boolean = false)
