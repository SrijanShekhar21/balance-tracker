package com.dbt.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Source
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEditSheet(vm: AppVm, txn: Txn, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var category by remember { mutableStateOf(txn.category) }
    var applyToAll by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                (if (txn.isCredit) "+" else "−") + Money.rupees(txn.amount, decimals = true),
                style = MaterialTheme.typography.headlineMedium,
                color = if (txn.isCredit) positiveColor() else MaterialTheme.colorScheme.onSurface
            )
            Text(
                txn.merchant.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${Days.label(txn.ts)} at ${Days.time(txn.ts)} · ${txn.channel}" +
                    (if (txn.account.isNotBlank()) " · A/c ${txn.account}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            txn.inferredFrom?.let { app ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "Category worked out from a $app message that arrived around the same " +
                        "time — the payee itself was not recognisable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Category", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.categories.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text(c, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remember this payee", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Apply to every past and future payment to ${txn.merchant}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = applyToAll, onCheckedChange = { applyToAll = it })
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    vm.setCategory(txn, category, applyToAll)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = category != txn.category || applyToAll
            ) { Text("Save") }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.deleteTxn(txn)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Delete this transaction") }

            if (txn.source == Source.SMS && txn.raw.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Original message",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    txn.raw,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Cash spends leave no SMS trail, so they are the one thing that must be typed in. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTxnSheet(vm: AppVm, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Categories.FOOD) }
    var isCredit by remember { mutableStateOf(false) }

    val parsed = amount.toDoubleOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Add a cash transaction", style = MaterialTheme.typography.titleMedium)
            Text(
                "Anything that did not go through your bank account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !isCredit,
                    onClick = { isCredit = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Spent") }
                SegmentedButton(
                    selected = isCredit,
                    onClick = { isCredit = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Received") }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount") },
                prefix = { Text("₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Paid to (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Category", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.categories.forEach { c ->
                    FilterChip(
                        selected = c == category,
                        onClick = { category = c },
                        label = { Text(c, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    parsed?.let {
                        vm.addManual(it, merchant, category, isCredit, System.currentTimeMillis())
                    }
                    onDismiss()
                },
                enabled = parsed != null && parsed > 0,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add") }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing()
    }
}
