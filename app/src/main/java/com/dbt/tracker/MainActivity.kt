package com.dbt.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dbt.tracker.data.Txn
import com.dbt.tracker.ui.AddTxnSheet
import com.dbt.tracker.ui.AppTheme
import com.dbt.tracker.ui.AppVm
import com.dbt.tracker.ui.HomeScreen
import com.dbt.tracker.ui.SettingsScreen
import com.dbt.tracker.ui.TriageScreen
import com.dbt.tracker.ui.TxnEditSheet
import com.dbt.tracker.ui.TxnScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) { Root() }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Report", Icons.Default.Today),
    LEDGER("Ledger", Icons.Default.ReceiptLong),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Bank exports carry unreliable MIME types -- the same file arrives as text/plain, as
 * application/octet-stream, or as a vendor Excel type depending on where it was downloaded --
 * so the picker stays open and the file's own leading bytes decide how it is read.
 */
private val PICKER_TYPES = arrayOf("*/*")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Root() {
    val vm: AppVm = viewModel()

    var tab by remember { mutableStateOf(Tab.TODAY) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var triaging by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importStatement(it) } }

    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.refresh()
    }

    vm.message?.let { text ->
        LaunchedEffect(text) {
            snackbar.showSnackbar(text)
            vm.message = null
        }
    }

    if (triaging) {
        TriageScreen(vm = vm, onClose = { triaging = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    when (tab) {
                        Tab.TODAY -> "Daily report"
                        Tab.LEDGER -> "All transactions"
                        Tab.SETTINGS -> "Settings"
                    }
                )
            })
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.SETTINGS) {
                FloatingActionButton(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add a cash transaction")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.TODAY -> HomeScreen(
                    vm = vm,
                    onTxnClick = { editing = it },
                    onTriage = { triaging = true },
                    onImport = { picker.launch(PICKER_TYPES) }
                )
                Tab.LEDGER -> TxnScreen(vm) { editing = it }
                Tab.SETTINGS -> SettingsScreen(
                    vm = vm,
                    onImport = { picker.launch(PICKER_TYPES) },
                    onTriage = { triaging = true }
                )
            }
        }
    }

    editing?.let { txn ->
        TxnEditSheet(vm = vm, txn = txn, onDismiss = { editing = null })
    }
    if (adding) {
        AddTxnSheet(vm = vm, onDismiss = { adding = false })
    }
}
