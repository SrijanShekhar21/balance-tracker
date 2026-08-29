package com.dbt.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money

/**
 * Bulk categorisation for spends the app could not place on its own.
 *
 * Built around ticking rather than tapping one at a time, because the payments that land here
 * arrive in runs: a month of Rapido riders is twenty different payees and one category. Tick
 * them, choose once, done.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TriageScreen(vm: AppVm, onClose: () -> Unit) {
    val pending = vm.triage
    val selectedCount = vm.selected.size

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Needs a category") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (pending.isNotEmpty()) {
                            TextButton(onClick = {
                                if (selectedCount == pending.size) vm.clearSelection() else vm.selectAll()
                            }) {
                                Text(if (selectedCount == pending.size) "None" else "All")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (selectedCount > 0) AssignBar(vm, selectedCount)
            }
        ) { padding ->
            if (pending.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Everything in the last 45 days is categorised.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Scaffold
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "${pending.size} payments could not be matched to a known merchant — " +
                            "usually someone's personal UPI code. Tick the ones that belong " +
                            "together and assign them in one go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(pending, key = { it.id }) { txn ->
                    TriageRow(
                        txn = txn,
                        checked = txn.id in vm.selected,
                        onToggle = { vm.toggleSelect(txn.id) }
                    )
                }

                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriageRow(txn: Txn, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    txn.merchant.ifBlank { "Unknown payee" },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${Days.label(txn.ts)} · ${txn.channel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.rupees(txn.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** The categories most likely to be wanted here sit first; the rest follow in order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignBar(vm: AppVm, count: Int) {
    val priority = listOf(
        Categories.TRANSPORT,
        Categories.GROCERY,
        Categories.FOOD,
        Categories.SHOPPING,
        Categories.TRANSFER
    )
    val ordered = priority + vm.categories.filterNot { it in priority }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "Assign $count selected to",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ordered.forEach { cat ->
                    AssistChip(
                        onClick = { vm.assignSelected(cat) },
                        label = { Text(cat, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .width(10.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(androidx.compose.ui.graphics.Color(Categories.colorOf(cat)))
                            )
                        }
                    )
                }
            }
        }
    }
}
