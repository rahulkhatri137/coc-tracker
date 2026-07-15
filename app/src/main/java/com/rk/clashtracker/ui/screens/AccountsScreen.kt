package com.rk.clashtracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.clashtracker.data.AccountEntity
import com.rk.clashtracker.ui.ClashViewModel
import com.rk.clashtracker.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: ClashViewModel,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClashObsidian)
            .padding(16.dp)
    ) {
        if (accounts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("empty_accounts_view"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "No accounts",
                    tint = TextSecondary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Villages Connected",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Add your Clash of Clans profiles to track their upgrades.",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                    modifier = Modifier.testTag("add_account_empty_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add First Village", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "Connected Villages",
                    color = ClashGold,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(accounts, key = { it.tag }) { account ->
                        AccountItem(
                            account = account,
                            onEdit = { newName, newTh -> viewModel.updateAccount(account.tag, newName, newTh) },
                            onDelete = { viewModel.deleteAccount(account.tag) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ClashGold,
                contentColor = Color.Black,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 16.dp, end = 16.dp)
                    .testTag("add_account_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }

        if (showAddDialog) {
            AddAccountDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { tag, name, thLevel ->
                    viewModel.addAccount(tag, name, thLevel)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun AccountItem(
    account: AccountEntity,
    onEdit: (newName: String, newTownHall: Int) -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = ClashSlate),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.tag}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Town Hall Shield/Badge Placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClashWood)
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TH",
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp
                        )
                        Text(
                            text = account.townHallLevel.toString(),
                            color = ClashGoldLight,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = account.name,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = account.tag,
                        color = ClashGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.testTag("edit_account_button_${account.tag}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Account", tint = ClashGoldLight)
                }

                IconButton(
                    onClick = { showConfirmDelete = true },
                    modifier = Modifier.testTag("delete_account_button_${account.tag}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = Color(0xFFEF5350))
                }
            }
        }
    }

    if (showEditDialog) {
        EditAccountDialog(
            account = account,
            onDismiss = { showEditDialog = false },
            onSave = { newName, newTh ->
                onEdit(newName, newTh)
                showEditDialog = false
            }
        )
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Profile?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove the village profile '${account.name}' and delete all its active and completed upgrades from the tracker.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showConfirmDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", color = ClashGold)
                }
            },
            containerColor = ClashSlateLight
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onSave: (newName: String, newTownHall: Int) -> Unit
) {
    var name by remember { mutableStateOf(account.name) }
    var thLevel by remember { mutableStateOf(account.townHallLevel) }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Village Profile",
                color = ClashGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Player Tag: ${account.tag}",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nickname (e.g. Main Account)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_account_name_input")
                )

                Column {
                    Text(
                        text = "Town Hall Level: $thLevel",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = thLevel.toFloat(),
                        onValueChange = { thLevel = it.toInt() },
                        valueRange = 1f..18f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = ClashGold,
                            activeTrackColor = ClashGold,
                            inactiveTrackColor = ClashSlateLight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_account_th_slider")
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
                    if (name.isBlank()) {
                        errorText = "Nickname cannot be empty."
                    } else {
                        onSave(name.trim(), thLevel)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                modifier = Modifier.testTag("confirm_edit_account_button")
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_edit_account_button")
            ) {
                Text("Cancel", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAdd: (tag: String, name: String, townHallLevel: Int) -> Unit
) {
    var tag by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var thLevel by remember { mutableStateOf(11) } // Default to Town Hall 11
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add Village Profile",
                color = ClashGold,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = {
                        tag = it
                        if (it.isNotEmpty()) errorText = ""
                    },
                    label = { Text("Player Tag (e.g. #P8Y9R2)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_tag_input")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nickname (e.g. Main Account)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClashGold,
                        focusedLabelColor = ClashGold,
                        cursorColor = ClashGold
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_name_input")
                )

                Column {
                    Text(
                        text = "Town Hall Level: $thLevel",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Slider(
                        value = thLevel.toFloat(),
                        onValueChange = { thLevel = it.toInt() },
                        valueRange = 1f..18f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = ClashGold,
                            activeTrackColor = ClashGold,
                            inactiveTrackColor = ClashSlateLight
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_th_slider")
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
                    if (tag.isBlank()) {
                        errorText = "Player tag cannot be empty."
                    } else {
                        onAdd(tag.trim(), name.trim(), thLevel)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ClashGold),
                modifier = Modifier.testTag("confirm_add_account_button")
            ) {
                Text("Connect", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_add_account_button")
            ) {
                Text("Cancel", color = ClashGold)
            }
        },
        containerColor = ClashSlate
    )
}