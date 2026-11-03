package com.nomi.wear

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Recheck even without a phone update, including after a timezone or clock change. */
internal fun Flow<WatchToday?>.withDayRollover(
    currentDate: () -> LocalDate = { LocalDate.now() },
): Flow<WatchToday?> = combine(
    flow {
        while (true) {
            emit(currentDate())
            delay(60_000)
        }
    }.distinctUntilChanged(),
) { snapshot, date -> snapshot?.asOf(date) }
