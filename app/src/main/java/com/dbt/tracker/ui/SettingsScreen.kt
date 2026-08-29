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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
fun SettingsScreen(
    vm: AppVm,
    hasSmsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTriage: () -> Unit
) {
    val s = vm.settings

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Panel("Reading your SMS") {
                if (!hasSmsPermission) {
                    Text(
                        "SMS access is off, so nothing is being tracked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant SMS access")
                    }
                } else {
                    Text(
                        "Active. ${vm.txnCount} transactions recorded.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(
                            onClick = { vm.rescan() },
                            enabled = !vm.busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (vm.busy) "Scanning..." else "Scan last ${s.backfillDays} days")
                        }
                        if (vm.busy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(Modifier.height(20.dp).width(20.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Safe to run any time. Transactions already recorded are skipped, " +
                            "so this only fills gaps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Panel("Categorisation") {
                Text(
                    if (vm.triage.isEmpty())
                        "Every recent spend has a category."
                    else
                        "${vm.triage.size} recent payments went to personal UPI codes the app " +
                            "could not place. Until they are sorted, your category breakdown " +
                            "understates where the money actually went.",
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
            Panel("Nightly report") {
                SettingRow(
                    "Report time",
                    "When the summary lands in your notifications"
                ) {
                    TimeStepper(
                        hour = s.reportHour,
                        minute = s.reportMinute,
                        onChange = { h, m ->
                            vm.updateSettings { reportHour = h; reportMinute = m }
                        }
                    )
                }
                SettingRow(
                    "Alert on every payment",
                    "A quiet notification as each transaction is recorded"
                ) {
                    Switch(
                        checked = s.liveAlerts,
                        onCheckedChange = { v -> vm.updateSettings { liveAlerts = v } }
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
                    label = "Monthly budget",
                    help = "Drives the budget bar and the overspend warning",
                    value = s.monthlyBudget
                ) { v -> vm.updateSettings { monthlyBudget = v } }

                MoneyField(
                    label = "Low balance alert",
                    help = "Flags the day when your balance drops below this",
                    value = s.lowBalance
                ) { v -> vm.updateSettings { lowBalanceThreshold = v } }

                MoneyField(
                    label = "Large payment alert",
                    help = "Any single payment at or above this is called out",
                    value = s.largeTxn
                ) { v -> vm.updateSettings { largeTxnThreshold = v } }
            }
        }

        item {
            val d = vm.diagnostics
            Panel("Balance workings", trailing = "why this number") {
                if (d == null || d.anchorBalance == null) {
                    Text(
                        "No balance has been read from your messages yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    DiagLine("Bank last said", Money.rupees(d.anchorBalance, decimals = true))
                    DiagLine("On", Days.label(d.anchorTs ?: 0L) + " " + Days.time(d.anchorTs ?: 0L))
                    DiagLine("For account", "ending " + d.anchorAccount.orEmpty().ifBlank { "unknown" })
                    DiagLine("Since then", "${d.txnsSinceAnchor} txns, ${Money.signed(d.netSinceAnchor)}")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "If the bank figure above is wrong or belongs to the wrong account, " +
                            "pick the right account below. If it is right but the total is not, " +
                            "the transactions since then are being miscounted — rebuild.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (d != null && d.accounts.isNotEmpty()) {
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
                                    "ending ${a.account}" + if (isPrimary) "  • tracked" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${a.channel} · ${a.txnCount} txns · ${a.balanceSightings} with a balance",
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

                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = { vm.rebuild() },
                    enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (vm.busy) "Rebuilding..." else "Delete all and re-read my SMS") }
                Text(
                    "Parsing fixes only apply to messages read after the fix. A rebuild replays " +
                        "your whole inbox through the current logic. Your category corrections " +
                        "are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item {
            Panel("Balance") {
                Text(
                    "Your balance comes from the figure SBI stamps on its alerts. Until one " +
                        "arrives, the app can work forward from a starting balance you enter here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                var opening by remember(s.openingBalance) {
                    mutableStateOf(s.openingBalance?.toLong()?.toString() ?: "")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = opening,
                        onValueChange = { opening = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Balance right now") },
                        prefix = { Text("₹") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { opening.toDoubleOrNull()?.let { vm.setOpeningBalance(it) } },
                        enabled = opening.toDoubleOrNull() != null
                    ) { Text("Set") }
                }
            }
        }

        item {
            Panel("Scanning") {
                SettingRow(
                    "History depth",
                    "How far back a scan reaches: ${s.backfillDays} days"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            vm.updateSettings {
                                backfillDays = (backfillDays - 30).coerceAtLeast(30)
                            }
                        }) { Icon(Icons.Default.Remove, contentDescription = "Less history") }
                        Text("${s.backfillDays}d", style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = {
                            vm.updateSettings {
                                backfillDays = (backfillDays + 30).coerceAtMost(365)
                            }
                        }) { Icon(Icons.Default.Add, contentDescription = "More history") }
                    }
                }
                SettingRow(
                    "Include other banks and wallets",
                    "Off means SBI messages only"
                ) {
                    Switch(
                        checked = s.includeAllSenders,
                        onCheckedChange = { v -> vm.updateSettings { includeAllSenders = v } }
                    )
                }
            }
        }

        item {
            Panel("Your data") {
                Text(
                    "Everything stays on this phone. The app has no internet permission at all, " +
                        "so your transactions cannot be uploaded anywhere, by it or by anything " +
                        "inside it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            // Step back 30 minutes, wrapping through the day.
            val total = (hour * 60 + minute - 30 + 1440) % 1440
            onChange(total / 60, total % 60)
        }) { Icon(Icons.Default.Remove, contentDescription = "Earlier") }

        Text(
            "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.bodyLarge
        )

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
