package com.nomi.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.nomi.app.ui.localization.nomiString

/**
 * A field for an API key: masked until asked, with the eye that every password field has.
 *
 * A key is forty-odd characters pasted from another app. Masked with no way to look, the only
 * check that the paste landed whole is to save it and see what the provider says.
 */
@Composable
fun NomiSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    // Not saveable on purpose: a revealed key should be hidden again after anything that
    // rebuilds the screen.
    var revealed by remember { mutableStateOf(false) }
    NomiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        enabled = enabled,
        isError = isError,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }, enabled = enabled) {
                Icon(
                    imageVector = if (revealed) {
                        Icons.Outlined.VisibilityOff
                    } else {
                        Icons.Outlined.Visibility
                    },
                    contentDescription = nomiString(if (revealed) "Hide key" else "Show key"),
                )
            }
        },
    )
}

/**
 * Keeps the window out of screenshots and the recents preview while a key can be on screen.
 *
 * Counted rather than set and cleared, because two screens that both ask for it overlap for the
 * length of a navigation transition, and the one leaving must not unprotect the one arriving.
 */
@Composable
fun NomiSecureWindow() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (secureWindowHolders++ == 0) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (--secureWindowHolders == 0) {
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** Touched only from composition effects, which run on the main thread. */
private var secureWindowHolders = 0

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
