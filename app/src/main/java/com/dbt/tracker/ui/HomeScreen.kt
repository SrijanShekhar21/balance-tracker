package com.dbt.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dbt.tracker.data.DayReport
import com.dbt.tracker.data.Severity
import com.dbt.tracker.data.Txn
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    vm: AppVm,
    onTxnClick: (Txn) -> Unit,
    onTriage: () -> Unit,
    onImport: () -> Unit
) {
    val r = vm.report

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { DayNavigator(vm) }

        if (r == null || (r.txnCount == 0 && vm.txnCount == 0)) {
            item { EmptyState(onImport) }
            return@LazyColumn
        }

        if (vm.triage.isNotEmpty()) {
            item { TriageBanner(count = vm.triage.size, onClick = onTriage) }
        }

        item { BalanceCard(r) }
        item { FlowCard(r) }
        item { PaceCard(r) }

        item {
            Panel("Spend over time", trailing = "last 30 days") {
                SpendTrendChart(points = r.trend, average = r.avg30)
            }
        }

        item {
            Panel(
                "This month so far",
                trailing = "day ${r.daysElapsedInMonth} of ${r.daysInMonth}"
            ) {
                MonthPaceChart(
                    monthSeries = r.monthSeries,
                    daysInMonth = r.daysInMonth,
                    projectedTotal = r.projectedMonthEnd,
                    budget = r.monthlyBudget
                )
            }
        }

        if (r.byCategory.isNotEmpty()) {
            item {
                Panel("Where it went", trailing = "${r.txnCount} txns") {
                    CategoryBars(r.byCategory)
                }
            }
        }

        if (r.topMerchants.isNotEmpty()) {
            item {
                Panel("Top payees") {
                    r.topMerchants.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (m.count > 1) "${m.merchant}  ×${m.count}" else m.merchant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                Money.rupees(m.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (r.flags.isNotEmpty()) {
            item {
                val critical = r.flags.count { it.severity != Severity.INFO }
                Panel("Red flags", trailing = if (critical > 0) "$critical need attention" else null) {
                    r.flags.forEach { FlagRow(it) }
                }
            }
        }

        item { MonthCard(r) }

        if (r.txns.isNotEmpty()) {
            item {
                Panel("Transactions") {
                    r.txns.forEachIndexed { i, t ->
                        TxnRow(t, onClick = { onTxnClick(t) })
                        if (i < r.txns.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

/**
 * Unplaced spends distort every number above, so this sits high in the page rather than being
 * tucked into settings where it would be ignored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriageBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$count payment${if (count == 1) "" else "s"} need a category",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Paid to personal UPI codes the app cannot recognise. Sort them in one go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DayNavigator(vm: AppVm) {
    val isToday = vm.viewDay == Days.todayStart()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { vm.shiftDay(-1) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Days.label(vm.viewDay),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                Days.full(vm.viewDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { vm.shiftDay(1) }, enabled = !isToday) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next day")
        }
    }

    // Without this, a day beyond the statement looks like a zero-spend day rather than a
    // day nothing is known about.
    vm.coveredUntil?.let { covered ->
        if (vm.viewDay > covered) {
            Text(
                "Your statement ends ${Days.label(covered)}. Nothing is known after that.",
                style = MaterialTheme.typography.bodySmall,
                color = warnColor(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun BalanceCard(r: DayReport) {
    Panel {
        Text(
            "Balance",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            r.balance?.let { Money.rupees(it, decimals = true) } ?: "Not known yet",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                r.balance == null -> "Set a starting balance in Settings, or wait for an SMS that quotes your balance."
                r.balanceIsEstimated -> "Estimated from transactions since your bank last quoted a balance."
                else -> "As last quoted by SBI."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        r.runwayDays?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                "About ${it.roundToInt()} days of runway at your recent pace",
                style = MaterialTheme.typography.bodySmall,
                color = if (it < 14) warnColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FlowCard(r: DayReport) {
    Panel("Cash flow") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Spent", Money.rupees(r.spent), Modifier.weight(1f))
            Stat("Received", Money.rupees(r.credited), Modifier.weight(1f), color = positiveColor())
            Stat(
                "Net",
                Money.signed(r.netFlow),
                Modifier.weight(1f),
                color = if (r.netFlow >= 0) positiveColor() else negativeColor()
            )
        }
        r.largest?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                "Largest: ${Money.rupees(it.amount)} to ${it.merchant}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Today measured against the user's own habit, which is the only baseline that means anything. */
@Composable
private fun PaceCard(r: DayReport) {
    val dayName = Days.label(r.dayStart)
    Panel("$dayName vs your average") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat(dayName, Money.rupees(r.spent), Modifier.weight(1f))
            Stat("7-day avg", Money.rupees(r.avg7), Modifier.weight(1f))
            Stat("30-day avg", Money.rupees(r.avg30), Modifier.weight(1f))
        }
        r.vsAvg30Pct?.let { pct ->
            Spacer(Modifier.height(12.dp))
            val over = pct > 0
            Text(
                when {
                    abs(pct) < 5 -> "Right on your usual daily spend."
                    over -> "${pct.roundToInt()}% above your 30-day average."
                    else -> "${abs(pct).roundToInt()}% below your 30-day average."
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when {
                    abs(pct) < 5 -> MaterialTheme.colorScheme.onSurfaceVariant
                    over -> negativeColor()
                    else -> positiveColor()
                }
            )
            Spacer(Modifier.height(8.dp))
            ComparisonBar(today = r.spent, average = r.avg30, todayLabel = dayName)
        }
    }
}

/** Two stacked bars sharing a scale: today's spend read against the 30-day norm. */
@Composable
private fun ComparisonBar(today: Double, average: Double, todayLabel: String) {
    val max = maxOf(today, average, 1.0)
    Column {
        BarLine(todayLabel, today / max, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        BarLine("Average", average / max, MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun BarLine(label: String, fraction: Double, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0.01f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun MonthCard(r: DayReport) {
    Panel(Days.month(r.dayStart), trailing = "day ${r.daysElapsedInMonth} of ${r.daysInMonth}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Spent", Money.short(r.mtdSpent), Modifier.weight(1f))
            Stat("Received", Money.short(r.mtdCredited), Modifier.weight(1f), color = positiveColor())
            Stat("Projected", Money.short(r.projectedMonthEnd), Modifier.weight(1f))
        }

        r.monthlyBudget?.let { budget ->
            if (budget > 0) {
                Spacer(Modifier.height(14.dp))
                val pct = (r.mtdSpent / budget).toFloat()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Budget ${Money.rupees(budget)}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${(pct * 100).roundToInt()}% used",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (pct > 1f) negativeColor()
                        else if (pct > 0.85f) warnColor()
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { pct.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = if (pct > 1f) negativeColor() else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            buildString {
                append(r.noSpendDaysThisMonth)
                append(" no-spend day")
                if (r.noSpendDaysThisMonth != 1) append("s")
                append(" so far")
                // SIP is counted in the total above; naming it here separates the month into
                // money consumed and money put away, which read very differently.
                if (r.sipMtd > 0) {
                    append("  ·  includes ")
                    append(Money.rupees(r.sipMtd))
                    append(" of SIP")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Panel("Getting started") {
        Text("No transactions yet.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Download your account statement from SBI net banking as CSV or Excel, then import " +
                "it. Every row carries its own closing balance, so nothing here is guessed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("Import a statement")
        }
    }
}
