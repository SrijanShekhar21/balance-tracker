package com.dbt.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.dbt.tracker.data.Txn
import com.dbt.tracker.ui.AddTxnSheet
import com.dbt.tracker.ui.AppTheme
import com.dbt.tracker.ui.AppVm
import com.dbt.tracker.ui.BalanceDialog
import com.dbt.tracker.ui.HomeScreen
import com.dbt.tracker.ui.PasswordDialog
import com.dbt.tracker.ui.SettingsScreen
import com.dbt.tracker.ui.SpendScreen
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
    TODAY("Report", Icons.Rounded.Today),
    SPEND("Spend", Icons.Rounded.PieChart),
    LEDGER("Ledger", Icons.Rounded.ReceiptLong),
    SETTINGS("Settings", Icons.Rounded.Settings)
}

/** Window in which a second back press means "exit" rather than a stray tap. */
private const val EXIT_WINDOW_MS = 2000L

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
    val scope = rememberCoroutineScope()

    val snackbar = remember { SnackbarHostState() }
    val activity = LocalContext.current as? android.app.Activity
    var lastBackPress by remember { mutableStateOf(0L) }

    // Back unwinds the app before it leaves it: an open sheet, then a sub-screen, then the
    // way back to the report, and only then a confirmed exit. Losing a half-finished import
    // to a stray back press would be worse than one extra tap.
    BackHandler(enabled = true) {
        when {
            vm.askingPassword -> vm.cancelPassword()
            vm.askingBalance -> vm.askingBalance = false
            adding -> adding = false
            editing != null -> editing = null
            triaging -> triaging = false
            tab != Tab.TODAY -> tab = Tab.TODAY
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPress < EXIT_WINDOW_MS) {
                    activity?.finish()
                } else {
                    lastBackPress = now
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("Press back again to exit")
                    }
                }
            }
        }
    }

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
        vm.start()
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
                        Tab.SPEND -> "Where money goes"
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
                    Icon(Icons.Rounded.Add, contentDescription = "Add a cash transaction")
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
                Tab.SPEND -> SpendScreen(vm) { editing = it }
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
    if (vm.askingPassword) {
        PasswordDialog(vm = vm, onDismiss = { vm.cancelPassword() })
    }
    if (vm.askingBalance) {
        BalanceDialog(vm = vm, onDismiss = { vm.askingBalance = false })
    }
}
