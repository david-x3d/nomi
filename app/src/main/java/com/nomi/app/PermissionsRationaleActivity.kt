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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nomi.app.ui.localization.LocalNomiLanguage
import com.nomi.app.ui.localization.NomiLanguage
import com.nomi.app.ui.localization.nomiString
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
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                nomiString("Health Connect privacy"),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                nomiString("Nomi uses Health Connect only when you choose to connect it."),
            )
            Text(
                nomiString("Nomi reads today's step count and active calories. With past-data access, it also imports your complete available weight history; otherwise Health Connect limits the import to its standard recent window."),
            )
            Text(
                nomiString("Nomi estimates calories from steps locally using your latest weight and, when available, your height. This estimate is kept separate from Health Connect active calories."),
            )
            Text(
                nomiString("Nomi writes the weight measurements that you manually save in Nomi and retries pending measurements later. A failed Health Connect write never removes the weight from Nomi."),
            )
            Text(
                nomiString("Nomi also writes your complete food log as nutrition entries: the calories, protein, carbohydrates and fat of each logged portion, with its name and meal. Editing or deleting food in Nomi updates or removes the matching Health Connect entry."),
            )
            Text(
                nomiString("Your Health Connect data is stored in Nomi's local database. Nomi does not sell or upload this health data."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                nomiString("You can revoke any permission at any time in Health Connect settings."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text(nomiString("Close"))
            }
        }
    }
}
