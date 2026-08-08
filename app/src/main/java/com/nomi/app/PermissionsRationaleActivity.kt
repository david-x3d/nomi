package com.nomi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.nomi.app.ui.theme.NomiTheme


class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NomiTheme {
                HealthConnectRationale(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun HealthConnectRationale(onClose: () -> Unit) {
    val german = LocalConfiguration.current.locales[0].language.equals("de", ignoreCase = true)
    fun text(english: String, germanText: String): String = if (german) germanText else english

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text("Health Connect privacy", "Health-Connect-Datenschutz"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text(
                    "Nomi uses Health Connect only when you choose to connect it.",
                    "Nomi verwendet Health Connect nur, wenn du die Verbindung selbst aktivierst.",
                ),
            )
            Text(
                text(
                    "Nomi reads weight measurements from the last 30 days and today's step count and active calories. This lets Nomi show your recent health information and weight trend.",
                    "Nomi liest Gewichtsmessungen der letzten 30 Tage sowie die heutige Schrittzahl und aktive Kalorien. So kann Nomi deine aktuellen Gesundheitsdaten und deinen Gewichtsverlauf anzeigen.",
                ),
            )
            Text(
                text(
                    "Nomi writes only weight measurements that you manually save in Nomi. A failed Health Connect write never removes the weight from Nomi.",
                    "Nomi schreibt nur Gewichtsmessungen, die du manuell in Nomi speicherst. Ein fehlgeschlagener Health-Connect-Schreibvorgang entfernt das Gewicht niemals aus Nomi.",
                ),
            )
            Text(
                text(
                    "Your Health Connect data is stored in Nomi's local database. Nomi does not sell or upload this health data. Your nutrition log is not shared with Health Connect.",
                    "Deine Health-Connect-Daten werden in der lokalen Nomi-Datenbank gespeichert. Nomi verkauft oder überträgt diese Gesundheitsdaten nicht. Dein Ernährungstagebuch wird nicht mit Health Connect geteilt.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text(
                    "You can revoke any permission at any time in Health Connect settings.",
                    "Du kannst jede Berechtigung jederzeit in den Health-Connect-Einstellungen widerrufen.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(text("Close", "Schließen"))
            }
        }
    }
}
