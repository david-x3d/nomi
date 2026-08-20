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
                    "Nomi reads today's step count and active calories. With past-data access, it also imports your complete available weight history; otherwise Health Connect limits the import to its standard recent window.",
                    "Nomi liest die heutige Schrittzahl und aktive Kalorien. Mit Zugriff auf vergangene Daten importiert Nomi außerdem deinen vollständig verfügbaren Gewichtsverlauf; andernfalls gilt das übliche aktuelle Zeitfenster von Health Connect.",
                ),
            )
            Text(
                text(
                    "Nomi estimates calories from steps locally using your latest weight and, when available, your height. This estimate is kept separate from Health Connect active calories.",
                    "Nomi schätzt Schrittkalorien lokal anhand deines aktuellen Gewichts und, falls vorhanden, deiner Größe. Diese Schätzung bleibt von den aktiven Kalorien aus Health Connect getrennt.",
                ),
            )
            Text(
                text(
                    "Nomi writes the weight measurements that you manually save in Nomi and retries pending measurements later. A failed Health Connect write never removes the weight from Nomi.",
                    "Nomi schreibt die Gewichtsmessungen, die du manuell in Nomi speicherst, und versucht ausstehende Messungen später erneut. Ein fehlgeschlagener Health-Connect-Schreibvorgang entfernt das Gewicht niemals aus Nomi.",
                ),
            )
            Text(
                text(
                    "Nomi also writes your complete food log as nutrition entries: the calories, protein, carbohydrates and fat of each logged portion, with its name and meal. Editing or deleting food in Nomi updates or removes the matching Health Connect entry.",
                    "Nomi schreibt außerdem dein vollständiges Ernährungstagebuch als Ernährungseinträge: Kalorien, Eiweiß, Kohlenhydrate und Fett jeder erfassten Portion samt Name und Mahlzeit. Wenn du ein Lebensmittel in Nomi änderst oder löschst, wird der zugehörige Health-Connect-Eintrag aktualisiert oder entfernt.",
                ),
            )
            Text(
                text(
                    "Your Health Connect data is stored in Nomi's local database. Nomi does not sell or upload this health data.",
                    "Deine Health-Connect-Daten werden in der lokalen Nomi-Datenbank gespeichert. Nomi verkauft oder überträgt diese Gesundheitsdaten nicht.",
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
