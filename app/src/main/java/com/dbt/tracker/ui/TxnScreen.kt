package com.dbt.tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money

/**
 * Full ledger, newest first, grouped by day with each day's net movement in the header so
 * the list can be skimmed without opening anything.
 */
@Composable
fun TxnScreen(vm: AppVm, onTxnClick: (Txn) -> Unit) {
    val grouped = vm.allTxns.groupBy { Days.startOfDay(it.ts) }.toSortedMap(compareByDescending<Long> { it })

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (vm.allTxns.isEmpty()) {
            item {
                Panel("Transactions") {
                    EmptyNote("Nothing recorded yet. Run a scan from Settings to import your SMS history.")
                }
            }
            return@LazyColumn
        }

        grouped.forEach { (day, list) ->
            val spent = list.filter { !it.isCredit }.sumOf { it.amount }
            val received = list.filter { it.isCredit }.sumOf { it.amount }

            item {
                Panel(
                    title = Days.label(day),
                    trailing = buildString {
                        append("−${Money.rupees(spent)}")
                        if (received > 0) append("   +${Money.rupees(received)}")
                    }
                ) {
                    list.forEachIndexed { i, t ->
                        TxnRow(t, onClick = { onTxnClick(t) })
                        if (i < list.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    "${vm.txnCount} transactions recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}
