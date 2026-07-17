package com.rk.clashtracker.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val defaultAccountTag by viewModel.defaultAccountTag.collectAsState()

    val accountNamesMap = remember(accounts) {
        accounts.associate { it.tag to it.name }
    }

    var selectedAccountTag by remember { mutableStateOf("All") }
    var showFilterDropdown by remember { mutableStateOf(false) }

    var showAddManualDialog by remember { mutableStateOf(false) }
    var showImportJsonDialog by remember { mutableStateOf(false) }
    var showImportScreenshotDialog by remember { mutableStateOf(false) }
    var showPotionBoostDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedUpgradeIds = remember { mutableStateListOf<Int>() }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var isDeleteAllConfirmed by remember { mutableStateOf(false) }

    val filteredUpgrades = upgrades.filter { upgrade ->
        selectedAccountTag == "All" || upgrade.accountTag == selectedAccountTag
    }

    val ongoingUpgrades = filteredUpgrades.filter { !it.isCompleted }
    val completedUpgrades = filteredUpgrades.filter { it.isCompleted }
    val currentTabUpgrades = if (selectedTab == 0) ongoingUpgrades else completedUpgrades

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClashObsidian)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Upgrade Planner",
                color = ClashGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )

            // Profile Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    Button(
                        onClick = { showFilterDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ClashSlate),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .border(1.dp, ClashBronze.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
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
                                fontSize = 12.sp,
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

                // Potion Boost dialog settings button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(ClashSlate, RoundedCornerShape(8.dp))
                        .border(1.dp, ClashBronze.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPotionBoostDialog = true }
                        .testTag("potion_boost_settings_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Potion Boost Settings",
                        tint = ClashGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ClashGold,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { 
                        selectedTab = 0 
                        isSelectionMode = false
                        selectedUpgradeIds.clear()
                    },
                    text = {
                        Text(
                            text = "Ongoing (${ongoingUpgrades.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    selectedContentColor = ClashGold,
                    unselectedContentColor = TextSecondary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { 
                        selectedTab = 1 
                        isSelectionMode = false
                        selectedUpgradeIds.clear()
                    },
                    text = {
                        Text(
                            text = "Completed (${completedUpgrades.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    selectedContentColor = ClashGold,
                    unselectedContentColor = TextSecondary
                )
            }

            // Bulk Actions / Selection Header Bar
            if (filteredUpgrades.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isSelectionMode) {
                        Text(
                            text = "Total: ${currentTabUpgrades.size}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // "Select" Button
                            TextButton(
                                onClick = { 
                                    isSelectionMode = true 
                                    selectedUpgradeIds.clear()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("select_multiple_button")
                            ) {
                                Icon(Icons.Default.List, contentDescription = null, tint = ClashGold, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Multiple", color = ClashGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // "Delete All" Button
                            TextButton(
                                onClick = {
                                    isDeleteAllConfirmed = true
                                    showDeleteConfirmationDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("delete_all_button")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete All", color = Color(0xFFEF5350), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Selection Mode Actions
                        Text(
                            text = "${selectedUpgradeIds.size} Selected",
                            color = ClashGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // "Select All" / "Clear" Button
                            val allSelected = selectedUpgradeIds.size == currentTabUpgrades.size && currentTabUpgrades.isNotEmpty()
                            TextButton(
                                onClick = {
                                    if (allSelected) {
                                        selectedUpgradeIds.clear()
                                    } else {
                                        selectedUpgradeIds.clear()
                                        selectedUpgradeIds.addAll(currentTabUpgrades.map { it.id })
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("select_all_toggle_button")
                            ) {
                                Text(
                                    text = if (allSelected) "Deselect All" else "Select All",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // "Delete Selected" Button
                            TextButton(
                                onClick = {
                                    if (selectedUpgradeIds.isNotEmpty()) {
                                        isDeleteAllConfirmed = false
                                        showDeleteConfirmationDialog = true
                                    }
                                },
                                enabled = selectedUpgradeIds.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("delete_selected_button")
                            ) {
                                Text(
                                    text = "Delete",
                                    color = if (selectedUpgradeIds.isNotEmpty()) Color(0xFFEF5350) else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // "Cancel" Button
                            TextButton(
                                onClick = { 
                                    isSelectionMode = false 
                                    selectedUpgradeIds.clear()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("cancel_selection_button")
                            ) {
                                Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Upgrades List
            if (currentTabUpgrades.isEmpty()) {
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
                        text = if (selectedTab == 0) "No Active Upgrades" else "No Completed Upgrades",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (selectedTab == 0) {
                            "Use the buttons below to log your upgrades manually or paste a JSON file."
                        } else {
                            "Completed upgrades will be stored here for your records."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(currentTabUpgrades, key = { it.id }) { upgrade ->
                        val accountName = remember(accountNamesMap, upgrade.accountTag) {
                            accountNamesMap[upgrade.accountTag] ?: upgrade.accountTag
                        }
                        UpgradeItem(
                            upgrade = upgrade,
                            accountName = accountName,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedUpgradeIds.contains(upgrade.id),
                            onToggleSelection = {
                                if (selectedUpgradeIds.contains(upgrade.id)) {
                                    selectedUpgradeIds.remove(upgrade.id)
                                } else {
                                    selectedUpgradeIds.add(upgrade.id)
                                }
                            },
                            onToggleComplete = { viewModel.toggleUpgradeCompletion(upgrade) },
                            onToggleLiveTracking = { viewModel.toggleLiveTracking(upgrade) },
                            onEditUpgrade = { name, remTime, lvl -> viewModel.updateUpgradeDetails(upgrade.id, name, remTime, lvl) },
                            onDelete = { viewModel.deleteUpgrade(upgrade.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(50.dp)) // padding for floating actions
                    }
                }
            }

            // Quick Import & Action Menu Banners at the bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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
                        .height(34.dp)
                        .testTag("import_json_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = ClashElixir, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste JSON", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                        .height(34.dp)
                        .testTag("manual_add_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manual Add", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Manual Add Dialog
        if (showAddManualDialog) {
            ManualAddDialog(
                accounts = accounts,
                defaultAccountTag = defaultAccountTag,
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
                onDismiss = { showImportJsonDialog = false },
                onImport = { jsonText ->
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
                defaultAccountTag = defaultAccountTag,
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

        // Delete Confirmation Dialog
        if (showDeleteConfirmationDialog) {
            val count = if (isDeleteAllConfirmed) currentTabUpgrades.size else selectedUpgradeIds.size
            val titleText = if (isDeleteAllConfirmed) "Delete All Upgrades" else "Delete Selected Upgrades"
            val messageText = if (isDeleteAllConfirmed) {
                "Are you sure you want to delete all $count upgrades in this section?"
            } else {
                "Are you sure you want to delete the selected $count upgrades?"
            }

            AlertDialog(
                onDismissRequest = { showDeleteConfirmationDialog = false },
                title = { Text(titleText, color = Color(0xFFEF5350), fontWeight = FontWeight.Bold) },
                text = { Text(messageText, color = TextPrimary) },
                confirmButton = {
                    Button(
                        onClick = {
                            val toDelete = if (isDeleteAllConfirmed) {
                                currentTabUpgrades
                            } else {
                                currentTabUpgrades.filter { selectedUpgradeIds.contains(it.id) }
                            }
                            viewModel.deleteMultipleUpgrades(toDelete)
                            
                            isSelectionMode = false
                            selectedUpgradeIds.clear()
                            showDeleteConfirmationDialog = false
                            Toast.makeText(context, "Deleted $count upgrades successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                        modifier = Modifier.testTag("confirm_delete_btn")
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmationDialog = false },
                        modifier = Modifier.testTag("cancel_delete_btn")
                    ) {
                        Text("Cancel", color = ClashGold)
                    }
                },
                containerColor = ClashSlate
            )
        }

        // Potion Boost Dialog
        if (showPotionBoostDialog) {
            PotionBoostDialog(
                accounts = accounts,
                upgrades = upgrades,
                initialSelectedAccount = selectedAccountTag,
                defaultAccountTag = defaultAccountTag,
                onDismiss = { showPotionBoostDialog = false },
                onApplyBoost = { accountTag, potionType ->
                    viewModel.applyPotionBoost(accountTag, potionType)
                    showPotionBoostDialog = false
                    val name = when (potionType) {
                        "Builder" -> "Builder Potion"
                        "Research" -> "Research Potion"
                        "Pet" -> "Pet Potion"
                        else -> "Potion"
                    }
                    Toast.makeText(context, "$name applied!", Toast.LENGTH_SHORT).show()
                },
                onApplyHelperBoost = { upgradeId, hours ->
                    viewModel.applyHelperBoost(upgradeId, hours)
                    showPotionBoostDialog = false
                    Toast.makeText(context, "Helper applied: $hours hours deducted!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun LiveTimerText(
    upgrade: UpgradeEntity,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    val remainingSeconds = remember(upgrade, currentTime) {
        val remainingMs = upgrade.endTime - currentTime
        (remainingMs / 1000).coerceAtLeast(0)
    }

    val timerText = if (upgrade.isCompleted) {
        "Completed 🔨"
    } else if (remainingSeconds <= 0) {
        "Finished! Pending Builder"
    } else {
        formatSecondsToDuration(remainingSeconds)
    }

    Text(
        text = timerText,
        color = if (upgrade.isCompleted) Color(0xFF4CAF50) else ClashGold,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

@Composable
fun LiveProgressBar(
    upgrade: UpgradeEntity,
    currentTime: Long,
    modifier: Modifier = Modifier
) {
    if (!upgrade.isCompleted) {
        val progress = remember(upgrade, currentTime) {
            if (upgrade.durationSeconds <= 0) 1f
            else {
                val elapsed = currentTime - upgrade.startTime
                val progressVal = elapsed.toFloat() / (upgrade.durationSeconds * 1000f)
                progressVal.coerceIn(0f, 1f)
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ClashGold,
            trackColor = ClashSlateLight
        )
    }
}

@Composable
fun UpgradeItem(
    upgrade: UpgradeEntity,
    accountName: String,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    onToggleComplete: () -> Unit,
    onToggleLiveTracking: () -> Unit,
    onEditUpgrade: (newName: String, newRemainingTime: String, targetLevel: Int?) -> Unit,
    onDelete: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(upgrade.isCompleted, upgrade.endTime) {
        if (!upgrade.isCompleted && upgrade.endTime > System.currentTimeMillis()) {
            while (true) {
                delay(1000)
                currentTime = System.currentTimeMillis()
            }
        }
    }

    val isTh = upgrade.villageType == "Town Hall"
    val villageBg = if (isTh) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFF065F46).copy(alpha = 0.25f)
    val villageTextCol = if (isTh) Color(0xFF93C5FD) else Color(0xFF6EE7B7)

    val categoryBg = when (upgrade.categoryType) {
        "Building" -> Color(0xFF78350F).copy(alpha = 0.25f)
        "Hero" -> Color(0xFF6B21A8).copy(alpha = 0.25f)
        "Pet" -> Color(0xFF3730A3).copy(alpha = 0.25f)
        "Troop" -> Color(0xFF831843).copy(alpha = 0.25f)
        else -> Color(0xFF4B5563).copy(alpha = 0.25f)
    }
    val categoryTextCol = when (upgrade.categoryType) {
        "Building" -> Color(0xFFFDE68A)
        "Hero" -> Color(0xFFE9D5FF)
        "Pet" -> Color(0xFFC7D2FE)
        "Troop" -> Color(0xFFFBCFE8)
        else -> Color(0xFFD1D5DB)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (upgrade.isCompleted) ClashSlateLight.copy(alpha = 0.5f) else ClashSlate
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (upgrade.isCompleted) Color.Transparent else ClashBronze.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp)
            )
            .testTag("upgrade_item_${upgrade.id}")
            .let { modifier ->
                if (isSelectionMode) {
                    modifier.clickable { onToggleSelection() }
                } else {
                    modifier
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ClashGold,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = Color.Black
                    ),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .testTag("checkbox_${upgrade.id}")
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Header: Nickname tag + completion buttons
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

                    if (!isSelectionMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Manual Live Tracking Notification Toggle (if active)
                            if (!upgrade.isCompleted) {
                                IconButton(
                                    onClick = onToggleLiveTracking,
                                    modifier = Modifier.size(52.dp).testTag("live_tracking_btn_${upgrade.id}")
                                ) {
                                    Icon(
                                        imageVector = if (upgrade.isLiveTracking) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                        contentDescription = "Live Notification Progress Tracker",
                                        tint = if (upgrade.isLiveTracking) ClashGold else TextSecondary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }

                            // Edit button
                            IconButton(
                                onClick = { showEditDialog = true },
                                modifier = Modifier.size(52.dp).testTag("edit_upgrade_btn_${upgrade.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Upgrade",
                                    tint = ClashGoldLight,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // Quick Complete Switch/Button
                            IconButton(
                                onClick = onToggleComplete,
                                modifier = Modifier.size(52.dp).testTag("complete_toggle_btn_${upgrade.id}")
                            ) {
                                Icon(
                                    imageVector = if (upgrade.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Complete Toggle",
                                    tint = if (upgrade.isCompleted) Color(0xFF4CAF50) else TextSecondary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(52.dp).testTag("delete_upgrade_btn_${upgrade.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFEF5350).copy(alpha = 0.8f),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Body: Structure name & target level and Village & Category chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = upgrade.structureName,
                        color = if (upgrade.isCompleted) TextSecondary else TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Village Tag (Town Hall or Builder Hall)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(villageBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = upgrade.villageType,
                                color = villageTextCol,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Category Tag (Building, Hero, Pet, Troop)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(categoryBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = upgrade.categoryType,
                                color = categoryTextCol,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (upgrade.targetLevel != null && upgrade.targetLevel > 0) {
                    Text(
                        text = "Level ${upgrade.targetLevel}",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (upgrade.isCompleted) "Status" else "Time Left",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LiveTimerText(upgrade = upgrade, currentTime = currentTime)
                }

                // Material 3 Live Progress Indicator (if active)
                if (!upgrade.isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LiveProgressBar(upgrade = upgrade, currentTime = currentTime)
                }
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
    
    val context = LocalContext.current
    val suggestions = remember(structureName) {
        if (structureName.isBlank()) emptyList()
        else JsonParser.getAutofillSuggestions(context.assets, structureName)
    }
    
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

                if (suggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClashSlateLight)
                            .border(1.dp, ClashGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        suggestions.forEachIndexed { index, suggestion ->
                            if (index > 0) {
                                Divider(color = ClashSlate.copy(alpha = 0.5f), thickness = 1.dp)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        structureName = suggestion
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Suggestion",
                                    tint = ClashGold.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = suggestion,
                                    color = ClashGoldLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("autofill_suggestion_$suggestion")
                                )
                            }
                        }
                    }
                }

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
    defaultAccountTag: String = "",
    onDismiss: () -> Unit,
    onAdd: (accountTag: String, name: String, targetLevel: Int?, durationSeconds: Long) -> Unit
) {
    val initialAccount = remember(defaultAccountTag, accounts) {
        if (defaultAccountTag.isNotEmpty() && accounts.any { it.tag == defaultAccountTag }) {
            defaultAccountTag
        } else {
            accounts.firstOrNull()?.tag ?: ""
        }
    }
    var selectedAccount by remember(initialAccount) { mutableStateOf(initialAccount) }
    var structureName by remember { mutableStateOf("") }
    var targetLevelString by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val suggestions = remember(structureName) {
        if (structureName.isBlank()) emptyList()
        else JsonParser.getAutofillSuggestions(context.assets, structureName)
    }
    
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

                if (suggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClashSlateLight)
                            .border(1.dp, ClashGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        suggestions.forEachIndexed { index, suggestion ->
                            if (index > 0) {
                                Divider(color = ClashSlate.copy(alpha = 0.5f), thickness = 1.dp)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        structureName = suggestion
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Suggestion",
                                    tint = ClashGold.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = suggestion,
                                    color = ClashGoldLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("autofill_suggestion_$suggestion")
                                )
                            }
                        }
                    }
                }

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
    onDismiss: () -> Unit,
    onImport: (jsonText: String) -> Unit
) {
    var jsonText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Upgrades from JSON", color = ClashGold, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
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
                    if (jsonText.isBlank()) {
                        errorText = "Please paste a JSON array or object."
                    } else {
                        onImport(jsonText)
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
    defaultAccountTag: String = "",
    onDismiss: () -> Unit,
    onConfirmImport: (accountTag: String, selectedUpgrades: List<JsonParser.ExtractedUpgrade>) -> Unit
) {
    val initialAccount = remember(defaultAccountTag, accounts) {
        if (defaultAccountTag.isNotEmpty() && accounts.any { it.tag == defaultAccountTag }) {
            defaultAccountTag
        } else {
            accounts.firstOrNull()?.tag ?: ""
        }
    }
    var selectedAccount by remember(initialAccount) { mutableStateOf(initialAccount) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotionBoostDialog(
    accounts: List<AccountEntity>,
    upgrades: List<UpgradeEntity>,
    initialSelectedAccount: String,
    defaultAccountTag: String = "",
    onDismiss: () -> Unit,
    onApplyBoost: (accountTag: String, potionType: String) -> Unit,
    onApplyHelperBoost: (upgradeId: Int, hours: Int) -> Unit
) {
    val context = LocalContext.current

    val accountsWithOngoingUpgrades = remember(accounts, upgrades) {
        accounts.filter { account ->
            upgrades.any { !it.isCompleted && it.accountTag == account.tag }
        }
    }

    val initialAccount = remember(accountsWithOngoingUpgrades, initialSelectedAccount, defaultAccountTag) {
        val prefAccount = if (defaultAccountTag.isNotEmpty() && accounts.any { it.tag == defaultAccountTag }) {
            defaultAccountTag
        } else {
            initialSelectedAccount
        }
        if (prefAccount == "All") "All"
        else if (accountsWithOngoingUpgrades.any { it.tag == prefAccount }) prefAccount
        else if (accountsWithOngoingUpgrades.isNotEmpty()) accountsWithOngoingUpgrades.first().tag
        else "All"
    }
    var selectedAccount by remember(initialAccount) { mutableStateOf(initialAccount) }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var showHelperDialog by remember { mutableStateOf(false) }

    val tryApplyPotion = { potionType: String, affectedCategories: List<String> ->
        val hasUpgrades = upgrades.any { upgrade ->
            !upgrade.isCompleted &&
            upgrade.villageType == "Town Hall" &&
            (selectedAccount == "All" || upgrade.accountTag == selectedAccount) &&
            upgrade.categoryType in affectedCategories
        }
        if (hasUpgrades) {
            onApplyBoost(selectedAccount, potionType)
        } else {
            val label = when (potionType) {
                "Builder" -> "ongoing Builder or Hero upgrades"
                "Research" -> "ongoing Laboratory research"
                "Pet" -> "ongoing Pet upgrades"
                else -> "upgrades"
            }
            Toast.makeText(context, "No $label to boost for this profile!", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PotionBottleIcon(color = ClashElixir, modifier = Modifier.size(24.dp))
                Text(
                    text = "Potion Boost Controls",
                    color = ClashGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Boost Town Hall village upgrades by instantly skipping hours.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                // Account Selection
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Target Profile",
                        color = ClashGoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val displayName = if (selectedAccount == "All") "All Profiles" else {
                            accounts.find { it.tag == selectedAccount }?.name ?: selectedAccount
                        }
                        OutlinedButton(
                            onClick = { dropdownExpanded = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .border(1.dp, ClashBronze.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(displayName, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ClashGold, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(ClashSlate)
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Profiles", color = TextPrimary, fontSize = 12.sp) },
                                onClick = {
                                    selectedAccount = "All"
                                    dropdownExpanded = false
                                }
                            )
                            accountsWithOngoingUpgrades.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name, color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        selectedAccount = account.tag
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Row Button: Builder Potion
                val builderBlue = Color(0xFF29B6F6) // Clear ocean blue
                Button(
                    onClick = {
                        tryApplyPotion("Builder", listOf("Building", "Hero"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, builderBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("builder_potion_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(builderBlue.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔨", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Builder Potion",
                                color = builderBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Skips 9 hours for Buildings & Heroes",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(builderBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("-9h", color = builderBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Row Button: Research Potion
                Button(
                    onClick = {
                        tryApplyPotion("Research", listOf("Troop"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, ClashElixir.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("research_potion_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClashElixir.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🧪", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Research Potion",
                                color = ClashElixirLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Skips 23 hours for Troops",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ClashElixirLight.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("-23h", color = ClashElixirLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Row Button: Pet Potion
                val petPotionGrey = Color(0xFFECEFF1) // Light pearlescent milky grey
                Button(
                    onClick = {
                        tryApplyPotion("Pet", listOf("Pet"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, petPotionGrey.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .testTag("pet_potion_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(petPotionGrey.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🐾", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pet Potion",
                                color = petPotionGrey,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Skips 23 hours for Pets",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(petPotionGrey.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("-23h", color = petPotionGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Row Button: Helper (Joker)
                val helperOrange = Color(0xFFFF9800) // Orange accent
                Button(
                    onClick = {
                        showHelperDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashSlateLight),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(1.dp, helperOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .testTag("helper_boost_btn")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(helperOrange.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            HelperHutIcon(modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Helper Boost",
                                color = helperOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Deducts custom hours from a single ongoing upgrade",
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(helperOrange.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Helper", color = helperOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_potion_dialog_btn")
            ) {
                Text("Close", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )

    if (showHelperDialog) {
        val ongoingUpgradesForSelectedAccount = upgrades.filter { upgrade ->
            !upgrade.isCompleted && (selectedAccount == "All" || upgrade.accountTag == selectedAccount)
        }
        HelperDialog(
            accounts = accounts,
            ongoingUpgrades = ongoingUpgradesForSelectedAccount,
            onDismiss = { showHelperDialog = false },
            onProceed = { upgradeId, hours ->
                onApplyHelperBoost(upgradeId, hours)
                showHelperDialog = false
            }
        )
    }
}

@Composable
fun PotionBottleIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Cork / Wood Stopper
        val corkPath = Path().apply {
            moveTo(width * 0.4f, height * 0.05f)
            lineTo(width * 0.6f, height * 0.05f)
            lineTo(width * 0.58f, height * 0.2f)
            lineTo(width * 0.42f, height * 0.2f)
            close()
        }
        drawPath(corkPath, color = Color(0xFFD7CCC8)) // Tan wooden cork

        // Neck of the bottle
        val neckPath = Path().apply {
            moveTo(width * 0.38f, height * 0.2f)
            lineTo(width * 0.62f, height * 0.2f)
            lineTo(width * 0.62f, height * 0.35f)
            lineTo(width * 0.38f, height * 0.35f)
            close()
        }
        drawPath(neckPath, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx()))

        // Body of the glass bottle (flask style)
        val bodyPath = Path().apply {
            moveTo(width * 0.38f, height * 0.35f)
            lineTo(width * 0.2f, height * 0.48f)
            lineTo(width * 0.2f, height * 0.92f)
            lineTo(width * 0.8f, height * 0.92f)
            lineTo(width * 0.8f, height * 0.48f)
            lineTo(width * 0.62f, height * 0.35f)
            close()
        }
        // Draw liquid inside first
        val liquidPath = Path().apply {
            moveTo(width * 0.22f, height * 0.58f)
            lineTo(width * 0.22f, height * 0.9f)
            lineTo(width * 0.78f, height * 0.9f)
            lineTo(width * 0.78f, height * 0.58f)
            quadraticTo(width * 0.5f, height * 0.54f, width * 0.22f, height * 0.58f)
            close()
        }
        drawPath(liquidPath, color = color.copy(alpha = 0.85f))

        // Draw glass bottle outline
        drawPath(bodyPath, color = Color.White.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx()))

        // Reflection highlight on the left
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(width * 0.28f, height * 0.6f),
            end = Offset(width * 0.28f, height * 0.85f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelperDialog(
    accounts: List<AccountEntity>,
    ongoingUpgrades: List<UpgradeEntity>,
    onDismiss: () -> Unit,
    onProceed: (upgradeId: Int, hours: Int) -> Unit
) {
    var selectedUpgrade by remember { mutableStateOf<UpgradeEntity?>(ongoingUpgrades.firstOrNull()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var hoursSelected by remember { mutableFloatStateOf(8f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HelperHutIcon(modifier = Modifier.size(24.dp))
                Text(
                    text = "Configure Helper",
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select an ongoing upgrade and choose how many hours to skip.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                if (ongoingUpgrades.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ongoing upgrades found for this profile.",
                            color = Color.Red.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Reset selected upgrade if it's not in the new list anymore
                    LaunchedEffect(ongoingUpgrades) {
                        if (selectedUpgrade == null || !ongoingUpgrades.any { it.id == selectedUpgrade?.id }) {
                            selectedUpgrade = ongoingUpgrades.firstOrNull()
                        }
                    }

                    // Upgrade Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Target Upgrade",
                            color = ClashGoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            val displayName = selectedUpgrade?.let { up ->
                                val profileName = accounts.find { it.tag == up.accountTag }?.name ?: up.accountTag
                                "${up.structureName} (Lvl ${up.targetLevel ?: "?"}) [$profileName]"
                            } ?: "Select Upgrade"

                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .border(1.dp, ClashBronze.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        displayName,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = ClashGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .background(ClashSlate)
                                    .fillMaxWidth(0.85f)
                            ) {
                                ongoingUpgrades.forEach { up ->
                                    val profileName = accounts.find { it.tag == up.accountTag }?.name ?: up.accountTag
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "${up.structureName} (Lvl ${up.targetLevel ?: "?"}) [$profileName]",
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        onClick = {
                                            selectedUpgrade = up
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hours to Deduct",
                                color = ClashGoldLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${hoursSelected.toInt()} Hours",
                                color = Color(0xFFFF9800),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Slider(
                            value = hoursSelected,
                            onValueChange = { hoursSelected = it },
                            valueRange = 1f..12f,
                            steps = 10,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFFF9800),
                                activeTrackColor = Color(0xFFFF9800),
                                inactiveTrackColor = ClashSlateLight,
                                activeTickColor = Color.Transparent,
                                inactiveTickColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("1h", color = TextSecondary, fontSize = 10.sp)
                            Text("12h", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedUpgrade?.let { up ->
                        onProceed(up.id, hoursSelected.toInt())
                    }
                },
                enabled = selectedUpgrade != null,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
            ) {
                Text("Proceed", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("Cancel")
            }
        },
        containerColor = ClashSlate
    )
}

@Composable
fun HelperHutIcon(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Left chimney pipe
        val chimneyPath = Path().apply {
            moveTo(w * 0.22f, h * 0.7f)
            lineTo(w * 0.22f, h * 0.4f)
            quadraticTo(w * 0.22f, h * 0.28f, w * 0.28f, h * 0.26f)
            lineTo(w * 0.32f, h * 0.26f)
            lineTo(w * 0.32f, h * 0.21f)
            lineTo(w * 0.26f, h * 0.21f)
            lineTo(w * 0.26f, h * 0.25f)
            quadraticTo(w * 0.17f, h * 0.28f, w * 0.17f, h * 0.4f)
            lineTo(w * 0.17f, h * 0.7f)
            close()
        }
        drawPath(chimneyPath, color = Color(0xFF90A4AE)) // metallic gray chimney

        // 2. Main Wooden Support Poles & Stone Base Blocks
        // Base blocks
        drawRect(
            color = Color(0xFF546E7A),
            topLeft = Offset(w * 0.25f, h * 0.75f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.2f)
        )
        drawRect(
            color = Color(0xFF546E7A),
            topLeft = Offset(w * 0.6f, h * 0.75f),
            size = androidx.compose.ui.geometry.Size(w * 0.15f, h * 0.2f)
        )

        // Wooden frame pillars (behind the roof but on base)
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(w * 0.32f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.35f)
        )
        drawRect(
            color = Color(0xFF5D4037),
            topLeft = Offset(w * 0.58f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.1f, h * 0.35f)
        )

        // Center Hut body wall
        drawRect(
            color = Color(0xFFD7CCC8),
            topLeft = Offset(w * 0.38f, h * 0.5f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.45f)
        )

        // 3. Pointed Yellow-Orange Canvas Roof (wizard-hat like)
        val roofPath = Path().apply {
            moveTo(w * 0.52f, h * 0.08f)
            quadraticTo(w * 0.26f, h * 0.25f, w * 0.2f, h * 0.56f)
            lineTo(w * 0.25f, h * 0.58f)
            quadraticTo(w * 0.52f, h * 0.4f, w * 0.75f, h * 0.58f)
            lineTo(w * 0.8f, h * 0.56f)
            quadraticTo(w * 0.74f, h * 0.25f, w * 0.52f, h * 0.08f)
            close()
        }
        drawPath(roofPath, color = Color(0xFFFFB300)) // Golden orange primary roof

        // Highlight/Stitch details on roof (laces)
        drawLine(
            color = Color(0xFFD84315),
            start = Offset(w * 0.36f, h * 0.25f),
            end = Offset(w * 0.46f, h * 0.22f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFD84315),
            start = Offset(w * 0.38f, h * 0.31f),
            end = Offset(w * 0.48f, h * 0.28f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFD84315),
            start = Offset(w * 0.40f, h * 0.37f),
            end = Offset(w * 0.50f, h * 0.34f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 4. Center Round Window
        val windowCenter = Offset(w * 0.5f, h * 0.55f)
        val outerRadius = w * 0.12f
        val innerRadius = w * 0.08f
        drawCircle(color = Color(0xFF8D6E63), radius = outerRadius, center = windowCenter) // wood rim
        drawCircle(color = Color(0xFFBBDEFB), radius = innerRadius, center = windowCenter) // glass blue

        // 5. Door Entrance at bottom with small orange awning roof
        // Awning roof (orange-red tiles)
        val awningPath = Path().apply {
            moveTo(w * 0.4f, h * 0.72f)
            lineTo(w * 0.5f, h * 0.65f)
            lineTo(w * 0.6f, h * 0.72f)
            close()
        }
        drawPath(awningPath, color = Color(0xFFFF5722)) // terracotta orange tile rooflet

        // Door frame and glowing dark entrance
        val doorPath = Path().apply {
            moveTo(w * 0.44f, h * 0.95f)
            lineTo(w * 0.44f, h * 0.78f)
            lineTo(w * 0.5f, h * 0.73f)
            lineTo(w * 0.56f, h * 0.78f)
            lineTo(w * 0.56f, h * 0.95f)
            close()
        }
        drawPath(doorPath, color = Color(0xFF3E2723)) // dark brown interior

        // Glow inside door
        drawRect(
            color = Color(0xFFFF8F00).copy(alpha = 0.6f),
            topLeft = Offset(w * 0.46f, h * 0.85f),
            size = androidx.compose.ui.geometry.Size(w * 0.08f, h * 0.1f)
        )

        // Left and right support ropes (hanging from roof corner to ground)
        drawLine(
            color = Color(0xFFD7CCC8),
            start = Offset(w * 0.22f, h * 0.57f),
            end = Offset(w * 0.18f, h * 0.8f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFD7CCC8),
            start = Offset(w * 0.78f, h * 0.57f),
            end = Offset(w * 0.82f, h * 0.8f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
