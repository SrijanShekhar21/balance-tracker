package com.dbt.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money

@Composable
fun SettingsScreen(vm: AppVm, onImport: () -> Unit, onTriage: () -> Unit) {
    val s = vm.settings

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel("Bank statement") {
                val covered = vm.coveredUntil
                Text(
                    if (covered == null) "No statement imported yet."
                    else "Data covers everything up to ${Days.label(covered)}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Download your statement from SBI net banking as CSV or Excel, then import " +
                        "it here. Importing a period again replaces it rather than duplicating, " +
                        "so you can re-import the current month as often as you like.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onImport,
                        enabled = !vm.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (vm.busy) "Reading..." else "Import a statement") }
                    if (vm.busy) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                    }
                }

                vm.lastImport?.let { r ->
                    Spacer(Modifier.height(12.dp))
                    if (r.ok) {
                        Text(
                            "Last import: ${r.imported} transactions, " +
                                "${Days.label(r.fromTs)} to ${Days.label(r.toTs)}" +
                                (if (r.replaced > 0) ", replacing ${r.replaced} earlier rows" else "") +
                                (if (r.account.isNotBlank()) ", account ending ${r.account}" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Warnings show even on success: a statement that parsed but silently
                    // skipped rows is exactly the case worth knowing about.
                    r.warnings.forEach { w ->
                        Spacer(Modifier.height(4.dp))
                        Text(w, style = MaterialTheme.typography.bodySmall, color = warnColor())
                    }
                    r.error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        item {
            Panel("Categorisation") {
                Text(
                    if (vm.triage.isEmpty()) "Every recent spend has a category."
                    else "${vm.triage.size} payments went to payees the app could not place. " +
                        "Until they are sorted, your category breakdown understates where the " +
                        "money actually went.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onTriage,
                    enabled = vm.triage.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sort unmatched payments") }
            }
        }

        item {
            val d = vm.diagnostics
            Panel("Balance workings", trailing = "why this number") {
                if (d?.anchorBalance == null) {
                    Text(
                        "No balance yet. Import a statement and it is read straight from the " +
                            "closing balance column.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DiagLine("Statement closing balance", Money.rupees(d.anchorBalance, decimals = true))
                    DiagLine("As of", Days.label(d.anchorTs ?: 0L))
                    DiagLine("Account", "ending " + d.anchorAccount.orEmpty().ifBlank { "unknown" })
                    DiagLine(
                        "Added since",
                        if (d.txnsSinceAnchor == 0) "nothing"
                        else "${d.txnsSinceAnchor} entries, ${Money.signed(d.netSinceAnchor)}"
                    )
                }

                if (d != null && d.accounts.size > 1) {
                    Spacer(Modifier.height(14.dp))
                    Text("Accounts seen", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    d.accounts.forEach { a ->
                        val isPrimary = a.account == d.primaryAccount
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "ending ${a.account}" + if (isPrimary) "  ·  tracked" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${a.txnCount} transactions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isPrimary) {
                                OutlinedButton(onClick = { vm.setPrimaryAccount(a.account) }) {
                                    Text("Track")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Panel("Nightly report") {
                SettingRow("Report time", "When the summary lands in your notifications") {
                    TimeStepper(
                        hour = s.reportHour,
                        minute = s.reportMinute,
                        onChange = { h, m -> vm.updateSettings { reportHour = h; reportMinute = m } }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.previewTonightsReport() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Preview tonight's report now") }
            }
        }

        item {
            Panel("Limits and alerts") {
                MoneyField(
                    "Monthly budget",
                    "Drives the budget bar and the overspend warning",
                    s.monthlyBudget
                ) { v -> vm.updateSettings { monthlyBudget = v } }

                MoneyField(
                    "Low balance alert",
                    "Flags the day your balance drops below this",
                    s.lowBalance
                ) { v -> vm.updateSettings { lowBalanceThreshold = v } }

                MoneyField(
                    "Large payment alert",
                    "Any single payment at or above this is called out",
                    s.largeTxn
                ) { v -> vm.updateSettings { largeTxnThreshold = v } }
            }
        }

        item {
            Panel("Your data") {
                Text(
                    "Everything stays on this phone. The app has no internet permission at all, " +
                        "so your statement cannot be uploaded anywhere. It reads only the single " +
                        "file you pick, and nothing else on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { vm.clearEverything() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete all transactions") }
            }
        }

        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeStepper(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            val total = (hour * 60 + minute - 30 + 1440) % 1440
            onChange(total / 60, total % 60)
        }) { Icon(Icons.Default.Remove, contentDescription = "Earlier") }

        Text("%02d:%02d".format(hour, minute), style = MaterialTheme.typography.bodyLarge)

        IconButton(onClick = {
            val total = (hour * 60 + minute + 30) % 1440
            onChange(total / 60, total % 60)
        }) { Icon(Icons.Default.Add, contentDescription = "Later") }
    }
}

/** A rupee amount that clears to "not set" when emptied. */
@Composable
private fun MoneyField(
    label: String,
    help: String,
    value: Double?,
    onCommit: (Double?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.toLong()?.toString() ?: "") }

    Column(Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                text = raw.filter { it.isDigit() }
                onCommit(text.toDoubleOrNull())
            },
            label = { Text(label) },
            prefix = { Text("₹") },
            placeholder = { Text("Not set") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (value != null) "$help · currently ${Money.rupees(value)}" else help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
        )
    }
}
