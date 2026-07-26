package com.mj.yata.ui.screen.lock

import com.mj.yata.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    onUnlockClick: () -> Unit,
    pinAvailable: Boolean = false,
    onVerifyPin: suspend (String) -> Boolean = { false },
    onPinUnlocked: () -> Unit = {}
) {
    var showPinEntry by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onUnlockClick() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (showPinEntry) {
            PinEntryScreen(
                onVerifyPin = onVerifyPin,
                onCorrect = onPinUnlocked,
                onUseBiometricInstead = { showPinEntry = false; onUnlockClick() }
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "YATA is locked",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Unlock with your device credential to continue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                )
                Button(onClick = onUnlockClick) {
                    Text(stringResource(R.string.lock_unlock))
                }
                if (pinAvailable) {
                    TextButton(onClick = { showPinEntry = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.lock_use_pin_instead))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinEntryScreen(
    onVerifyPin: suspend (String) -> Boolean,
    onCorrect: () -> Unit,
    onUseBiometricInstead: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (digits.length < 4) return
        scope.launch {
            if (onVerifyPin(digits)) {
                onCorrect()
            } else {
                errorMessage = "Incorrect PIN"
                digits = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            repeat(digits.length.coerceAtMost(8)) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Text(
            text = errorMessage ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "backspace")
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { key ->
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "" -> {}
                            "backspace" -> IconButton(onClick = {
                                errorMessage = null
                                digits = digits.dropLast(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.lock_backspace))
                            }
                            else -> IconButton(onClick = {
                                errorMessage = null
                                if (digits.length < 8) digits += key
                            }) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 16.dp)) {
            IconButton(onClick = { submit() }) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_confirm_pin))
            }
        }

        TextButton(onClick = onUseBiometricInstead, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.lock_use_biometric_instead))
        }
    }
}
