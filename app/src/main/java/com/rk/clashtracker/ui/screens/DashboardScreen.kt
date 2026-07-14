package com.rk.clashtracker.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.clashtracker.data.*
import com.rk.clashtracker.ui.ClashViewModel
import com.rk.clashtracker.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ClashViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToUpgrades: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accounts by viewModel.accounts.collectAsState()
    val upgrades by viewModel.upgrades.collectAsState()

    // Live countdown trigger
    var tickTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tickTrigger++
        }
    }

    // Calculations
    val activeUpgrades = upgrades.filter { !it.isCompleted }
    val completedUpgrades = upgrades.filter { it.isCompleted }
    val totalUpgrades = upgrades.size

    val totalBuildersWorking = activeUpgrades.size
    val nextFinishingUpgrade = activeUpgrades
        .minByOrNull { it.endTime }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClashObsidian)
            .padding(16.dp)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero Dashboard Header Card with gradient glow
            item {
                DashboardHeroHeader(
                    workingBuilders = totalBuildersWorking,
                    connectedVillages = accounts.size,
                    completedUpgrades = completedUpgrades.size,
                    totalUpgrades = totalUpgrades
                )
            }

            // Next Finish Section
            item {
                key(tickTrigger) {
                    NextFinishCard(
                        nextUpgrade = nextFinishingUpgrade,
                        accounts = accounts,
                        onNavigateToUpgrades = onNavigateToUpgrades
                    )
                }
            }

            // Connected Villages Overview
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Village Status",
                        color = ClashGold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onNavigateToAccounts) {
                        Text("Manage", color = ClashGoldLight)
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ClashGoldLight)
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ClashSlate),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToAccounts)
                            .testTag("dashboard_empty_accounts_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.PeopleOutline, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No Villages Connected", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Connect a Clash profile to begin tracking progress.", color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                items(accounts, key = { it.tag }) { account ->
                    val accountUpgrades = upgrades.filter { it.accountTag == account.tag }
                    val accountWorkingBuilders = accountUpgrades.count { !it.isCompleted }
                    val accountCompleted = accountUpgrades.count { it.isCompleted }
                    val accountTotal = accountUpgrades.size
                    
                    VillageStatusCard(
                        account = account,
                        workingBuilders = accountWorkingBuilders,
                        completedCount = accountCompleted,
                        totalCount = accountTotal,
                        onClick = onNavigateToUpgrades
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // spacing bottom
            }
        }
    }
}

@Composable
fun DashboardHeroHeader(
    workingBuilders: Int,
    connectedVillages: Int,
    completedUpgrades: Int,
    totalUpgrades: Int
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(ClashGold.copy(alpha = 0.5f), Color.Transparent)),
                shape = RoundedCornerShape(24.dp)
            )
            .testTag("dashboard_hero_card")
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ClashSlateLight, ClashSlate)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Clash Empire",
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Synchronized upgrades overview",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Builder Hut badge
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ClashWood)
                            .border(1.dp, ClashGold, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = ClashGold, modifier = Modifier.size(18.dp))
                            Text(
                                text = "$workingBuilders",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        icon = Icons.Default.People,
                        label = "Villages",
                        value = "$connectedVillages",
                        color = ClashGold
                    )
                    StatItem(
                        icon = Icons.Default.Construction,
                        label = "Active",
                        value = "$workingBuilders",
                        color = ClashElixir
                    )
                    StatItem(
                        icon = Icons.Default.DoneAll,
                        label = "Finished",
                        value = "$completedUpgrades",
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aggregate Progress Indicator
                val progress = if (totalUpgrades > 0) completedUpgrades.toFloat() / totalUpgrades else 0f
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Aggregate Progress", color = TextSecondary, fontSize = 11.sp)
                        Text("${(progress * 100).toInt()}%", color = ClashGoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = TextSecondary, fontSize = 11.sp)
            Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NextFinishCard(
    nextUpgrade: UpgradeEntity?,
    accounts: List<AccountEntity>,
    onNavigateToUpgrades: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ClashSlate),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dashboard_next_finish_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Next Upgrade Finishing",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = ClashGold, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (nextUpgrade != null) {
                val account = accounts.find { it.tag == nextUpgrade.accountTag }
                val accountName = account?.name ?: nextUpgrade.accountTag
                
                val remaining = nextUpgrade.remainingSeconds
                val timerText = if (remaining <= 0) "Completed! 🔨" else formatSecondsToDuration(remaining)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = nextUpgrade.structureName,
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Village: $accountName",
                            color = ClashGoldLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClashSlateLight)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = timerText,
                            color = if (remaining <= 0) Color(0xFF4CAF50) else ClashGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Text(
                    text = "No upgrades currently in progress.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun VillageStatusCard(
    account: AccountEntity,
    workingBuilders: Int,
    completedCount: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = ClashSlate),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dashboard_village_card_${account.tag}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // TH Mini Icon
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ClashWood),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${account.townHallLevel}", color = ClashGoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(account.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(account.tag, color = TextSecondary, fontSize = 11.sp)
                    }
                }

                // Builders
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ClashSlateLight)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Construction, contentDescription = null, tint = ClashGold, modifier = Modifier.size(12.dp))
                    Text("$workingBuilders Working", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$completedCount / $totalCount Upgrades Done",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = ClashGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
