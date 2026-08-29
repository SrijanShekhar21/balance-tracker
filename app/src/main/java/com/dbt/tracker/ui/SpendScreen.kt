package com.dbt.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dbt.tracker.data.Bucket
import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.SpendPeriod
import com.dbt.tracker.util.Days
import com.dbt.tracker.util.Money
import kotlin.math.roundToInt

/**
 * Where the money goes, over a chosen period.
 *
 * Two levels: a category, and the payees inside it. That second level is the point --
 * "Groceries 8,400" is a fact you cannot act on, while "Zepto 6,100 across 19 orders" is a
 * decision. Categories stay collapsed by default so the page opens as a readable summary.
 */
@Composable
fun SpendScreen(vm: AppVm, onTxnClick: (com.dbt.tracker.data.Txn) -> Unit) {
    val view = vm.spendView

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PeriodPicker(vm) }

        if (view == null || view.buckets.isEmpty()) {
            item {
                Panel("Spending") {
                    EmptyNote("Nothing recorded in this period.")
                }
            }
            return@LazyColumn
        }

        item {
            Panel(view.period.label, trailing = "${view.txnCount} payments") {
                Text(
                    Money.rupees(view.total, decimals = true),
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Money.rupees(view.dailyAverage)} a day on average" +
                        if (view.received > 0) " · ${Money.rupees(view.received)} received" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                // A single stacked bar reads the whole split at a glance, before the list
                // repeats it as numbers.
                StackedShareBar(view.buckets)
            }
        }

        item {
            Panel("By category", trailing = "tap to open") {
                view.buckets.forEachIndexed { i, bucket ->
                    BucketRow(
                        bucket = bucket,
                        expanded = vm.expandedCategory == bucket.category,
                        onToggle = { vm.toggleCategory(bucket.category) },
                        largest = view.buckets.first().amount,
                        onMerchantClick = { merchant ->
                            vm.showMerchant(bucket.category, merchant)
                        },
                        openMerchant = vm.expandedMerchant,
                        merchantTxns = vm.merchantTxns,
                        onTxnClick = onTxnClick
                    )
                    if (i < view.buckets.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun PeriodPicker(vm: AppVm) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SpendPeriod.entries.forEach { p ->
            FilterChip(
                selected = vm.spendPeriod == p,
                onClick = { vm.setSpendPeriod(p) },
                label = { Text(p.label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

/** The full split as one bar. Segments are separated by a surface gap so they stay countable. */
@Composable
private fun StackedShareBar(buckets: List<Bucket>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        buckets.filter { it.share > 0.01 }.forEach { b ->
            Box(
                Modifier
                    .weight(b.share.toFloat())
                    .height(10.dp)
                    .background(Color(Categories.colorOf(b.category)))
            )
        }
    }
}

@Composable
private fun BucketRow(
    bucket: Bucket,
    expanded: Boolean,
    onToggle: () -> Unit,
    largest: Double,
    onMerchantClick: (String) -> Unit,
    openMerchant: String?,
    merchantTxns: List<com.dbt.tracker.data.Txn>,
    onTxnClick: (com.dbt.tracker.data.Txn) -> Unit
) {
    val color = Color(Categories.colorOf(bucket.category))
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(bucket.category, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${bucket.count} payments · ${(bucket.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                Money.rupees(bucket.amount),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.padding(start = 4.dp).rotate(arrow),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MiniBar(
            fraction = if (largest > 0) (bucket.amount / largest).toFloat() else 0f,
            color = color
        )

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)) {
                bucket.merchants.forEach { m ->
                    val open = openMerchant == m.merchant
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onMerchantClick(m.merchant) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.merchant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (m.count > 1) {
                                Text(
                                    "${m.count} payments · ${Money.rupees(m.amount / m.count)} each on average",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            Money.rupees(m.amount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Third level: the actual payments, so a surprising total can be traced
                    // all the way to the transaction that caused it.
                    AnimatedVisibility(visible = open) {
                        Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                            merchantTxns.forEach { t ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onTxnClick(t) }
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        Days.label(t.ts),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        Money.rupees(t.amount),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
