package com.rk.clashtracker.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.clashtracker.data.*
import com.rk.clashtracker.util.JsonParser
import com.rk.clashtracker.ui.ClashViewModel
import com.rk.clashtracker.ui.ParseState
import com.rk.clashtracker.ui.theme.*
import kotlinx.coroutines.delay
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradesScreen(
    viewModel: ClashViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    val upgrades by viewModel.upgrades.collectAsState()
    val parseState by viewModel.parseState.collectAsState()

    var selectedAccountTag by remember { mutableStateOf("All") }
    var showFilterDropdown by remember { mutableStateOf(false) }

    var showAddManualDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showImportScreenshotDialog by remember { mutableStateOf(false) }

    // Live countdown trigger: increments every second to force recomposition of countdown fields
    var tickTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickTrigger++
        }
    }

    val context = LocalContext.current

    val filteredUpgrades = upgrades.filter { upgrade ->
        selectedAccountTag == "All" || upgrade.accountTag == selectedAccountTag
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClashObsidian)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Heading
            Text(
                text = "Upgrade Planner",
                color = ClashGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Profile Filter
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = { showFilterDropdown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlate),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .border(1.dp, ClashBronze.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .testTag("account_filter_button")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedAccountTag == "All") "Showing: All Profiles" else "Showing Profile: $selectedAccountTag",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ClashGold)
                    }
                }

                DropdownMenu(
                    expanded = showFilterDropdown,
                    onDismissRequest = { showFilterDropdown = false },
                    modifier = Modifier.background(ClashSlate)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Profiles", color = TextPrimary) },
                        onClick = {
                            selectedAccountTag = "All"
                            showFilterDropdown = false
                        },
                        modifier = Modifier.testTag("filter_option_all")
                    )
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name, color = TextPrimary) },
                            onClick = {
                                selectedAccountTag = account.tag
                                showFilterDropdown = false
                            },
                            modifier = Modifier.testTag("filter_option_${account.tag}")
                        )
                    }
                }
            }

            // Upgrades List
            if (filteredUpgrades.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("empty_upgrades_view"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "No upgrades",
                        tint = TextSecondary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Active Upgrades",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use the buttons below to log your upgrades manually or paste a JSON file.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                // Key forces recomposition with ticker
                key(tickTrigger) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(filteredUpgrades, key = { it.id }) { upgrade ->
                            val accountName = accounts.find { it.tag == upgrade.accountTag }?.name ?: upgrade.accountTag
                            UpgradeItem(
                                upgrade = upgrade,
                                accountName = accountName,
                                onToggleComplete = { viewModel.toggleUpgradeCompletion(upgrade) },
                                onEditUpgrade = { name, remTime, lvl -> viewModel.updateUpgradeDetails(upgrade.id, name, remTime, lvl) },
                                onDelete = { viewModel.deleteUpgrade(upgrade.id) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(100.dp)) // padding for floating actions
                        }
                    }
                }
            }

            // Quick Import & Action Menu Banners at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Import JSON Button
                Button(
                    onClick = {
                        if (accounts.isEmpty()) {
                            Toast.makeText(context, "Please connect a village profile first!", Toast.LENGTH_LONG).show()
                        } else {
                            showImportJsonDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("import_json_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = ClashElixir, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste JSON", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                // Manual Add Button
                Button(
                    onClick = {
                        if (accounts.isEmpty()) {
                            Toast.makeText(context, "Please connect a village profile first!", Toast.LENGTH_LONG).show()
                        } else {
                            showAddManualDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("manual_add_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual Add", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Manual Add Dialog
        if (showAddManualDialog) {
            ManualAddDialog(
                accounts = accounts,
                onDismiss = { showAddManualDialog = false },
                onAdd = { accountTag, name, level, seconds ->
                    viewModel.addUpgrade(accountTag, name, level, seconds)
                    showAddManualDialog = false
                    Toast.makeText(context, "Upgrade scheduled!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Paste JSON Dialog
        if (showImportJsonDialog) {
            ImportJsonDialog(
                accounts = accounts,
                onDismiss = { showImportJsonDialog = false },
                onImport = { accountTag, jsonText ->
                    viewModel.parseManualJson(jsonText)
                    showImportJsonDialog = false
                    showImportScreenshotDialog = true // Reuse the review/import dialog!
                }
            )
        }

        // AI / JSON review and import confirmation Dialog
        if (showImportScreenshotDialog) {
            ReviewImportDialog(
                accounts = accounts,
                parseState = parseState,
                onDismiss = {
                    viewModel.resetParseState()
                    showImportScreenshotDialog = false
                },
                onConfirmImport = { accountTag, selectedUpgrades ->
                    viewModel.importParsedUpgrades(accountTag, selectedUpgrades)
                    showImportScreenshotDialog = false
                    Toast.makeText(context, "Successfully imported ${selectedUpgrades.size} upgrades!", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}

@Composable
fun UpgradeItem(
    upgrade: UpgradeEntity,
    accountName: String,
    onToggleComplete: () -> Unit,
    onEditUpgrade: (newName: String, newRemainingTime: String, targetLevel: Int?) -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (upgrade.isCompleted) ClashSlateLight.copy(alpha = 0.5f) else ClashSlate
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (upgrade.isCompleted) Color.Transparent else ClashBronze.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("upgrade_item_${upgrade.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Nickname tag + completion button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Account tag badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ClashWood)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = accountName,
                        color = ClashGoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Edit button
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("edit_upgrade_btn_${upgrade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Upgrade",
                            tint = ClashGoldLight
                        )
                    }

                    // Quick Complete Switch/Button
                    IconButton(
                        onClick = onToggleComplete,
                        modifier = Modifier.testTag("complete_toggle_btn_${upgrade.id}")
                    ) {
                        Icon(
                            imageVector = if (upgrade.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Complete Toggle",
                            tint = if (upgrade.isCompleted) Color(0xFF4CAF50) else TextSecondary
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_upgrade_btn_${upgrade.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF5350).copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body: Structure name & target level
            val levelText = if (upgrade.targetLevel != null && upgrade.targetLevel > 0) " (Lvl ${upgrade.targetLevel})" else ""
            Text(
                text = "${upgrade.structureName}$levelText",
                color = if (upgrade.isCompleted) TextSecondary else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Countdown / Info line (No progress bar)
            val remaining = upgrade.remainingSeconds
            val timerText = if (upgrade.isCompleted) {
                "Completed 🔨"
            } else if (remaining <= 0) {
                "Finished! Pending Builder"
            } else {
                formatSecondsToDuration(remaining)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (upgrade.isCompleted) "Status" else "Time Left",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    text = timerText,
                    color = if (upgrade.isCompleted) Color(0xFF4CAF50) else ClashGold,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    if (showEditDialog) {
        EditUpgradeDialog(
            upgrade = upgrade,
            onDismiss = { showEditDialog = false },
            onSave = { name, remTime, lvl ->
                onEditUpgrade(name, remTime, lvl)
                showEditDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditUpgradeDialog(
    upgrade: UpgradeEntity,
    onDismiss: () -> Unit,
    onSave: (newName: String, newRemainingTime: String, targetLevel: Int?) -> Unit
) {
    var structureName by remember { mutableStateOf(upgrade.structureName) }
    var targetLevelString by remember { mutableStateOf(upgrade.targetLevel?.toString() ?: "") }
    
    // Suggest the current remaining duration pre-filled in human readable format
    val currentRemaining = upgrade.remainingSeconds
    val currentRemainingStr = if (currentRemaining > 0) formatSecondsToDuration(currentRemaining) else ""
    var remainingTimeText by remember { mutableStateOf(currentRemainingStr) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Upgrade Details", color = ClashGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = structureName,
                    onValueChange = { structureName = it },
                    label = { Text("Structure Name (Data Entry)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetLevelString,
                    onValueChange = { targetLevelString = it },
                    label = { Text("Target Level (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = remainingTimeText,
                    onValueChange = { remainingTimeText = it },
                    label = { Text("Remaining Time (e.g. 3d 4h, 12h, 45m)") },
                    placeholder = { Text("Leave blank to keep current timer") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText.isNotEmpty()) {
                    Text(errorText, color = Color(0xFFEF5350), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (structureName.isBlank()) {
                        errorText = "Structure Name cannot be empty."
                    } else {
                        val lvl = targetLevelString.toIntOrNull()
                        onSave(structureName.trim(), remainingTimeText.trim(), lvl)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClashGold)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAddDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onAdd: (accountTag: String, name: String, targetLevel: Int?, durationSeconds: Long) -> Unit
) {
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()?.tag ?: "") }
    var structureName by remember { mutableStateOf("") }
    var targetLevelString by remember { mutableStateOf("") }
    
    // Duration selectors
    var days by remember { mutableStateOf("0") }
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("0") }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Builder Upgrade", color = ClashGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Account selector
                Box {
                    val accountName = accounts.find { it.tag == selectedAccount }?.name ?: selectedAccount
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Village: $accountName")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(ClashSlate)
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name, color = TextPrimary) },
                                onClick = {
                                    selectedAccount = account.tag
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Structure name
                OutlinedTextField(
                    value = structureName,
                    onValueChange = {
                        structureName = it
                        if (it.isNotEmpty()) errorText = ""
                    },
                    label = { Text("Structure Name (e.g. Mortar)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_structure_input")
                )

                // Level input
                OutlinedTextField(
                    value = targetLevelString,
                    onValueChange = { targetLevelString = it },
                    label = { Text("Target Upgrade Level (optional)") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_level_input")
                )

                // Time Pickers
                Text("Time Remaining:", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it.filter { char -> char.isDigit() } },
                        label = { Text("Days") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_days_input")
                    )
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { hours = it.filter { char -> char.isDigit() } },
                        label = { Text("Hours") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_hours_input")
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { char -> char.isDigit() } },
                        label = { Text("Min") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_mins_input")
                    )
                }

                if (errorText.isNotEmpty()) {
                    Text(errorText, color = Color(0xFFEF5350), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val daysVal = days.toLongOrNull() ?: 0L
                    val hoursVal = hours.toLongOrNull() ?: 0L
                    val minsVal = minutes.toLongOrNull() ?: 0L
                    val totalSeconds = (daysVal * 24 * 60 * 60) + (hoursVal * 60 * 60) + (minsVal * 60)

                    if (selectedAccount.isEmpty()) {
                        errorText = "Please select or add a Village profile first."
                    } else if (structureName.isBlank()) {
                        errorText = "Structure name is required."
                    } else if (totalSeconds <= 0) {
                        errorText = "Please enter an upgrade duration."
                    } else {
                        val lvl = targetLevelString.toIntOrNull()
                        onAdd(selectedAccount, structureName.trim(), lvl, totalSeconds)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                modifier = Modifier.testTag("manual_confirm_button")
            ) {
                Text("Schedule", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )
}

@Composable
fun ImportJsonDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onImport: (accountTag: String, jsonText: String) -> Unit
) {
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()?.tag ?: "") }
    var jsonText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Upgrades from JSON", color = ClashGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Account Selector
                Box {
                    val accountName = accounts.find { it.tag == selectedAccount }?.name ?: selectedAccount
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Into: $accountName")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(ClashSlate)
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name, color = TextPrimary) },
                                onClick = {
                                    selectedAccount = account.tag
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    "Format: {\"data\":1000014,\"lvl\":2,\"timer\":25479},{\"data\":1000000,\"lvl\":5,\"timer\":20382}",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        if (it.isNotEmpty()) errorText = ""
                    },
                    placeholder = { Text("{\n  \"data\": 1000014,\n  \"lvl\": 2,\n  \"timer\": 25479\n}", color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ClashGold, focusedLabelColor = ClashGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("json_paste_input")
                )

                if (errorText.isNotEmpty()) {
                    Text(errorText, color = Color(0xFFEF5350), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedAccount.isEmpty()) {
                        errorText = "Please connect an Account first."
                    } else if (jsonText.isBlank()) {
                        errorText = "Please paste a JSON array."
                    } else {
                        onImport(selectedAccount, jsonText)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                modifier = Modifier.testTag("json_confirm_import_button")
            ) {
                Text("Process", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )
}

@Composable
fun ReviewImportDialog(
    accounts: List<AccountEntity>,
    parseState: ParseState,
    onDismiss: () -> Unit,
    onConfirmImport: (accountTag: String, selectedUpgrades: List<JsonParser.ExtractedUpgrade>) -> Unit
) {
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()?.tag ?: "") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (parseState) {
                    is ParseState.Loading -> "Processing JSON Data... 🔮"
                    is ParseState.Error -> "Import Failed"
                    is ParseState.Success -> "Review Parsed Upgrades"
                    else -> "Processing"
                },
                color = ClashGold,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
            ) {
                when (parseState) {
                    is ParseState.Loading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = ClashGold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Parsing JSON object records and calculating upgrade timers...",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is ParseState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(parseState.message, color = TextPrimary, fontSize = 14.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Please verify your JSON structure format. Ensure it follows: {\"data\":1000014,\"lvl\":2,\"timer\":25479}.", color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }

                    is ParseState.Success -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Target Account Selector
                            Text("Assign these upgrades to profile:", color = TextSecondary, fontSize = 12.sp)
                            Box {
                                val accountName = accounts.find { it.tag == selectedAccount }?.name ?: selectedAccount
                                OutlinedButton(
                                    onClick = { dropdownExpanded = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(accountName)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }

                                DropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.background(ClashSlate)
                                ) {
                                    accounts.forEach { account ->
                                        DropdownMenuItem(
                                            text = { Text(account.name, color = TextPrimary) },
                                            onClick = {
                                                selectedAccount = account.tag
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Divider(color = ClashBronze.copy(alpha = 0.3f))

                            Text("Discovered Upgrades:", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(parseState.upgrades) { upgrade ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ClashSlateLight)
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val lvl = if (upgrade.targetLevel != null) " (Lvl ${upgrade.targetLevel})" else ""
                                            Text(
                                                text = "${upgrade.structureName}$lvl",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Time remaining: ${upgrade.timeLeftString}",
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Icon(Icons.Default.Check, contentDescription = "Valid", tint = Color(0xFF4CAF50))
                                    }
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
        },
        confirmButton = {
            if (parseState is ParseState.Success) {
                Button(
                    onClick = {
                        if (selectedAccount.isNotEmpty()) {
                            onConfirmImport(selectedAccount, parseState.upgrades)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                    modifier = Modifier.testTag("confirm_import_to_profile_button")
                ) {
                    Text("Import ${parseState.upgrades.size} Upgrades", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight)
                ) {
                    Text("OK", color = TextPrimary)
                }
            }
        },
        dismissButton = {
            if (parseState is ParseState.Success) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = ClashGold)
                }
            }
        },
        containerColor = ClashSlate
    )
}
