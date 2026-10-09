package com.nomi.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate

/** Writes a sample day the way the phone would, for emulators and screenshots. */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val request = PutDataMapRequest.create(WearContract.TODAY_PATH).apply {
            dataMap.putString(WearContract.KEY_DATE, LocalDate.now().toString())
            dataMap.putBoolean(WearContract.KEY_HAS_PROFILE, true)
            dataMap.putString(WearContract.KEY_LANGUAGE, intent.getStringExtra("language").orEmpty())
            dataMap.putDouble(WearContract.KEY_CALORIES, 1_240.0)
            dataMap.putDouble(WearContract.KEY_CALORIE_TARGET, 2_100.0)
            dataMap.putDouble(WearContract.KEY_PROTEIN, 82.0)
            dataMap.putDouble(WearContract.KEY_PROTEIN_TARGET, 140.0)
            dataMap.putDouble(WearContract.KEY_CARBS, 131.0)
            dataMap.putDouble(WearContract.KEY_CARBS_TARGET, 230.0)
            dataMap.putDouble(WearContract.KEY_FAT, 44.0)
            dataMap.putDouble(WearContract.KEY_FAT_TARGET, 70.0)
            dataMap.putDataMapArrayList(
                WearContract.KEY_QUICK,
                arrayListOf(
                    sample(WearContract.QUICK_FAVORITE, 1, "Skyr", "200 g", 126.0),
                    sample(WearContract.QUICK_FAVORITE, 2, "Banana", "1 piece", 105.0),
                    sample(WearContract.QUICK_SAVED_MEAL, 3, "Usual breakfast", "", 420.0),
                ),
            )
            dataMap.putLong(WearContract.KEY_UPDATED_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).addOnCompleteListener { pending.finish() }
    }

    private fun sample(kind: String, id: Long, title: String, subtitle: String, kcal: Double) = DataMap().apply {
        putString(WearContract.KEY_QUICK_KIND, kind)
        putLong(WearContract.KEY_QUICK_ID, id)
        putString(WearContract.KEY_QUICK_TITLE, title)
        putString(WearContract.KEY_QUICK_SUBTITLE, subtitle)
        putDouble(WearContract.KEY_QUICK_CALORIES, kcal)
    }
}
