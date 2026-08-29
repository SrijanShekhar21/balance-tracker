package com.dbt.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Asks for the statement password.
 *
 * The password is offered for saving because a bank reissues the same one every month, and it
 * opens a file already sitting on the phone rather than granting access to anything.
 */
@Composable
fun PasswordDialog(vm: AppVm, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf(vm.savedPassword) }
    var remember_ by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statement password") },
        text = {
            Column {
                Text(
                    if (vm.passwordFailed)
                        "That password did not open the file. Try again."
                    else
                        "This statement is encrypted. Enter the password SBI set on it and the " +
                            "app will open it directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (vm.passwordFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = remember_, onCheckedChange = { remember_ = it })
                    Text(
                        "Remember it for next month",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.submitPassword(password, remember_) },
                enabled = password.isNotBlank()
            ) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Asks for the balance once, when a statement lists transactions but no running balance.
 *
 * Computing forward from one known figure is exact here in a way it never was with SMS: a
 * statement is complete for its period, so there is nothing missing to accumulate error.
 */
@Composable
fun BalanceDialog(vm: AppVm, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What is your balance?") },
        text = {
            Column {
                Text(
                    "That statement lists transactions but no running balance, so the app cannot " +
                        "read one. Enter your balance as it stands now and every figure after it " +
                        "is calculated from the statement, which is complete.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Balance now") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { vm.submitBalance(it) } },
                enabled = amount != null && amount > 0
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}
