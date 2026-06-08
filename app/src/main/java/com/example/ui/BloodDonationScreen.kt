package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Donor
import com.example.data.EmergencyAlert
import com.example.util.LocationUtils
import com.example.ui.theme.LiveGreen
import com.example.ui.theme.SoftOrange
import com.example.ui.theme.BloodRed
import com.example.ui.theme.DonorCoral
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MeshBackground(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F111A) else Color(0xFFFCF8F8))
            .drawBehind {
                val width = size.width
                val height = size.height

                // Top right blur circle (vibrant red/pink)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF7F1D1D).copy(alpha = 0.28f), Color.Transparent)
                        } else {
                            listOf(Color(0xFFFFEEF0).copy(alpha = 0.9f), Color.Transparent)
                        },
                        center = androidx.compose.ui.geometry.Offset(width * 0.95f, -height * 0.05f),
                        radius = width * 0.85f
                    )
                )

                // Bottom left blur circle (pink/rose)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF831843).copy(alpha = 0.16f), Color.Transparent)
                        } else {
                            listOf(Color(0xFFFFF1F4).copy(alpha = 0.85f), Color.Transparent)
                        },
                        center = androidx.compose.ui.geometry.Offset(-width * 0.15f, height * 0.65f),
                        radius = width * 0.95f
                    )
                )
            }
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodDonationScreen(viewModel: BloodDonationViewModel) {
    val currentLat by viewModel.currentLat.collectAsState()
    val currentLon by viewModel.currentLon.collectAsState()
    val isSimulationActive by viewModel.isGpsSimulationActive.collectAsState()
    val locationPresetName by viewModel.selectedLocationPresetName.collectAsState()

    val selfProfile by viewModel.selfProfile.collectAsState()
    val otherDonors by viewModel.otherDonors.collectAsState()
    val emergencyAlerts by viewModel.emergencyAlerts.collectAsState()
    val matchedAlertPopup by viewModel.activeMatchAlert.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Live Matches", "My Profile", "Post Emergency")

    // Dynamic warning matching dialog for foreground notification simulation
    matchedAlertPopup?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAlertPopup(alert.id) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissAlertPopup(alert.id)
                        viewModel.showToast("Contact details saved. Call requester at: ${alert.contactNumber}")
                    },
                    modifier = Modifier.testTag("action_help_confirm")
                ) {
                    Text("I Option to Help", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAlertPopup(alert.id) }) {
                    Text("Decline Alert", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Urgent Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "NEW MATCH ALERT!",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                val distance = LocationUtils.calculateDistance(
                    currentLat, currentLon, alert.latitude, alert.longitude
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "A patient requires ${alert.bloodGroup} Blood!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Location: ${alert.locationName} (${String.format("%.1f", distance)} km away)",
                        fontWeight = FontWeight.Medium
                    )
                    if (alert.message.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "\"${alert.message}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    Text(
                        text = "This alert is within your configured 10 km notification safe zone. Your response could save a life.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true),
            modifier = Modifier.testTag("match_notification_dialog")
        )
    }

    // Custom Host-Level Toast System
    toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(4000)
            viewModel.clearToast()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 90.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Toast Icon",
                        tint = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    MeshBackground {
        val isDarkTheme = isSystemInDarkTheme()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Column(
                    modifier = Modifier
                        .background(
                            if (isDarkTheme) Color(0xFF141722).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.45f)
                        )
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "Blood Donors Link",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                                )
                            }
                            Text(
                                text = "Secure Location Coordinator",
                                fontSize = 11.sp,
                                color = (if (isDarkTheme) Color.White else Color(0xFF475569)).copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Seeding & Reset controls
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.resetAndSeed() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Seed Data",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearDatabase() },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear database",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Collapsible GPS Location Simulator Panel - Critical for Cloudy Emulators
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Coords",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Simulated Location: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)
                                    )
                                    Text(
                                        text = locationPresetName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "LAT: ${String.format("%.4f", currentLat)} | LON: ${String.format("%.4f", currentLon)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Simulation Coordinates Quick Presets Selection Grid
                            Text(
                                text = "Tap a preset below to move your coordinate anchor:",
                                fontSize = 10.sp,
                                color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val presets = listOf(
                                    Triple("Downtown Hospital (0.0km)", 40.7128, -74.0060),
                                    Triple("East Suburb (~3.1km)", 40.7258, -73.9860),
                                    Triple("Northville (~8.5km)", 40.7828, -73.9660),
                                    Triple("Airport (~23km)", 40.6413, -73.7781)
                                )
                                presets.forEach { (label, lat, lon) ->
                                    Button(
                                        onClick = { viewModel.updateLocationPreset(label.substringBefore(" ("), lat, lon) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(29.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (locationPresetName == label.substringBefore(" (")) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                                            },
                                            contentColor = if (locationPresetName == label.substringBefore(" (")) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                if (isDarkTheme) Color.White else Color(0xFF0F172A)
                                            }
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                    ) {
                                        Text(
                                            text = label.substringBefore(" "),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Smooth Horizontal Navigation Tabs
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp
                            )
                        }
                    ) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                },
                                icon = {
                                    when (index) {
                                        0 -> Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                        1 -> Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                        2 -> Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            },
            contentWindowInsets = WindowInsets.navigationBars
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (selectedTab) {
                    0 -> LiveDashboardTab(viewModel, currentLat, currentLon, selfProfile, otherDonors, emergencyAlerts)
                    1 -> MyProfileTab(viewModel, selfProfile)
                    2 -> CreateEmergencyAlertTab(viewModel)
                }
            }
        }
    }
}

// ==========================================
// TAB 1: LIVE MATCHES DASHBOARD
// ==========================================
@Composable
fun LiveDashboardTab(
    viewModel: BloodDonationViewModel,
    userLat: Double,
    userLon: Double,
    selfProfile: Donor?,
    donors: List<Donor>,
    alerts: List<EmergencyAlert>
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
    ) {
        // Status overview banner
        item {
            val isDarkTheme = isSystemInDarkTheme()
            val isReady = selfProfile != null && selfProfile.isAvailable
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selfProfile == null) {
                        if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                    } else if (isReady) {
                        if (isDarkTheme) Color(0xFF064E3B).copy(alpha = 0.35f) else Color(0xFFD1FAE5).copy(alpha = 0.65f)
                    } else {
                        if (isDarkTheme) Color(0xFF78350F).copy(alpha = 0.35f) else Color(0xFFFEF3C7).copy(alpha = 0.65f)
                    }
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selfProfile == null) {
                        if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                    } else if (isReady) {
                        if (isDarkTheme) Color(0xFF10B981).copy(alpha = 0.45f) else Color(0xFF10B981).copy(alpha = 0.75f)
                    } else {
                        if (isDarkTheme) Color(0xFFF59E0B).copy(alpha = 0.45f) else Color(0xFFF59E0B).copy(alpha = 0.75f)
                    }
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (selfProfile == null) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else if (isReady) {
                                    LiveGreen.copy(alpha = 0.2f)
                                } else {
                                    SoftOrange.copy(alpha = 0.2f)
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (selfProfile == null) {
                                MaterialTheme.colorScheme.primary
                            } else if (isReady) {
                                LiveGreen
                            } else {
                                SoftOrange
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selfProfile == null) "Guest Account (Unregistered)" else "Active Session: ${selfProfile.name}",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (selfProfile == null) {
                                "Register as a donor in the 'My Profile' tab to match nearby emergency alerts!"
                            } else if (selfProfile.isAvailable) {
                                "🟢 Online & Ready: Matching ${selfProfile.bloodGroup} alerts within a 10km radius."
                            } else {
                                "🟡 Standby Mode: Registered as ${selfProfile.bloodGroup}, but marked 'Not Ready'."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.75f) else Color(0xFF475569)
                        )
                    }
                }
            }
        }

        // Section 1: Active Emergency Alerts List
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚨 LIVE EMERGENCY BROADCASTS (${alerts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Within 10 km target",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (alerts.isEmpty()) {
            item {
                val isDarkTheme = isSystemInDarkTheme()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Safe",
                            tint = LiveGreen,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Active Emergencies!",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "There are currently no reported active emergency requests in this anchor range.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(alerts) { alert ->
                // Math distance
                val distance = LocationUtils.calculateDistance(userLat, userLon, alert.latitude, alert.longitude)
                val isMatchedMyBlood = selfProfile != null && selfProfile.bloodGroup.equals(alert.bloodGroup, ignoreCase = true)
                val isWithinRadius = distance <= 10.0
                val isCriticalDirectMatch = isMatchedMyBlood && isWithinRadius && selfProfile?.isAvailable == true

                val isDarkTheme = isSystemInDarkTheme()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isCriticalDirectMatch) {
                                Modifier.background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(BloodRed, DonorCoral)
                                    ),
                                    shape = RoundedCornerShape(28.dp)
                                )
                            } else {
                                Modifier
                            }
                        ),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCriticalDirectMatch) {
                            Color.Transparent
                        } else {
                            if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (isCriticalDirectMatch) 1.5.dp else 1.dp,
                        color = if (isCriticalDirectMatch) {
                            Color.White.copy(alpha = 0.7f)
                        } else {
                            if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Large blood drop badge
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(
                                            color = if (isCriticalDirectMatch) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = alert.bloodGroup,
                                        color = if (isCriticalDirectMatch) Color.White else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = alert.locationName,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = if (isCriticalDirectMatch) Color.White else (if (isDarkTheme) Color.White else Color(0xFF0F172A))
                                    )
                                    Text(
                                        text = "By Contact: ${alert.contactNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isCriticalDirectMatch) Color.White.copy(alpha = 0.85f) else (if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569))
                                    )
                                }
                            }

                            // Distance Badge
                            Column(horizontalAlignment = Alignment.End) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCriticalDirectMatch) {
                                            Color.White.copy(alpha = 0.25f)
                                        } else if (isWithinRadius) {
                                            LiveGreen
                                        } else {
                                            if (isDarkTheme) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                                        }
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", distance)} km",
                                        color = if (isCriticalDirectMatch) Color.White else Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = if (isWithinRadius) "Nearby (Matched)" else "Outside 10km",
                                    fontSize = 9.sp,
                                    color = if (isCriticalDirectMatch) {
                                        Color.White
                                    } else if (isWithinRadius) {
                                        LiveGreen
                                    } else {
                                        if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        if (alert.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCriticalDirectMatch) Color.White.copy(alpha = 0.9f) else (if (isDarkTheme) Color.White.copy(alpha = 0.8f) else Color(0xFF334155))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isCriticalDirectMatch) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Matched",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Blood Group Match!",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (selfProfile != null && !isMatchedMyBlood) {
                                Text(
                                    text = "Required: ${alert.bloodGroup} | You: ${selfProfile.bloodGroup}",
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f),
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.showToast("Mock Call established to: ${alert.contactNumber}")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCriticalDirectMatch) Color.White else MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = if (isCriticalDirectMatch) BloodRed else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call Req", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { viewModel.deleteAlert(alert.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fulfill Request",
                                        tint = if (isCriticalDirectMatch) Color.White else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Other Registered Donors Directory
        item {
            Text(
                text = "👥 REGISTERED DONOR DIRECTORY (${donors.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        if (donors.isEmpty()) {
            item {
                val isDarkTheme = isSystemInDarkTheme()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No donor files available",
                            fontWeight = FontWeight.Bold,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A)
                        )
                        Text(
                            text = "Tap the Reset/Seed icon in the top right to populate predefined test records instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(donors) { donor ->
                // Calculate distance
                val distance = LocationUtils.calculateDistance(userLat, userLon, donor.latitude, donor.longitude)
                val isDarkTheme = isSystemInDarkTheme()
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (donor.isAvailable) {
                            if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                        } else {
                            if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f)
                        }
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Blood Group Insignia
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = if (donor.isAvailable) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = donor.bloodGroup,
                                    color = if (donor.isAvailable) MaterialTheme.colorScheme.primary else (if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569)),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = donor.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                        fontSize = 14.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = if (donor.isAvailable) LiveGreen else SoftOrange,
                                                shape = CircleShape
                                            )
                                    )
                                }
                                Text(
                                    text = "Dist: ${String.format("%.1f", distance)} km | Contact: ${donor.contactNumber}",
                                    fontSize = 11.sp,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569)
                                )
                                Text(
                                    text = if (donor.isAvailable) "Ready to Donate" else "Not Ready (Standby)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (donor.isAvailable) LiveGreen else SoftOrange
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.showToast("Simulated Call to donor: ${donor.contactNumber}") },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call Donor",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteDonor(donor.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete donor record",
                                    tint = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color(0xFF334155).copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: MY DONOR PROFILE (REGISTRATION)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileTab(viewModel: BloodDonationViewModel, profile: Donor?) {
    var regName by remember { mutableStateOf("") }
    var regContact by remember { mutableStateOf("") }
    var regBloodGroup by remember { mutableStateOf("O+") }
    var regIsAvailable by remember { mutableStateOf(true) }

    var expandedGroupDropdown by remember { mutableStateOf(false) }
    val bloodGroupsList = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    // Automatically fill inputs from database profile if it exists
    LaunchedEffect(profile) {
        profile?.let {
            regName = it.name
            regContact = it.contactNumber
            regBloodGroup = it.bloodGroup
            regIsAvailable = it.isAvailable
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🛡️ SECURE DONOR PROFILE",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        AnimatedContent(
            targetState = profile != null,
            label = "Registration Form Switch"
        ) { isRegistered ->
            if (isRegistered && profile != null) {
                // Registered Membership Card Visualizer
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Beautiful Membership Identity Tag
                    val isDarkTheme = isSystemInDarkTheme()
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Red header bar clipped nicely
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(BloodRed, DonorCoral)
                                        )
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "EMERGENCY REGISTERED DONOR",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "National Blood Registry Link",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "DONOR NAME",
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = profile.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                            fontSize = 18.sp
                                        )
                                    }

                                    // Huge droplet container for blood type
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = profile.bloodGroup,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 20.sp
                                            )
                                            Text(
                                                text = "TYPE",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "CONTACT MOBILE",
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = profile.contactNumber,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                            fontSize = 14.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "STATUS",
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = if (profile.isAvailable) "READY TO HELP" else "ON STANDBY",
                                            fontWeight = FontWeight.Bold,
                                            color = if (profile.isAvailable) LiveGreen else SoftOrange,
                                            fontSize = 13.sp
                                        )
                                    }
                                }

                                HorizontalDivider(color = if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Lat: ${String.format("%.4f", profile.latitude)} Lon: ${String.format("%.4f", profile.longitude)}",
                                            fontSize = 10.sp,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF334155),
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "ID: B-${profile.id + 1024}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // Toggles & Settings card for the registered donor
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Emergency Availability Controls",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (regIsAvailable) LiveGreen.copy(alpha = 0.1f) else SoftOrange.copy(
                                            alpha = 0.1f
                                        )
                                    )
                                    .clickable {
                                        regIsAvailable = !regIsAvailable
                                        viewModel.toggleLocalAvailability(regIsAvailable)
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (regIsAvailable) Icons.Default.CheckCircle else Icons.Default.DoNotDisturb,
                                        contentDescription = null,
                                        tint = if (regIsAvailable) LiveGreen else SoftOrange
                                    )
                                    Column {
                                        Text(
                                            text = if (regIsAvailable) "Available & Beacon Active" else "Beacon Stopped (Standby)",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (regIsAvailable) "You get alerts for matching blood requests near you." else "Emergency requesters cannot match your coordinates.",
                                            fontSize = 11.sp,
                                            color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569)
                                        )
                                    }
                                }
                                Switch(
                                    checked = regIsAvailable,
                                    onCheckedChange = {
                                        regIsAvailable = it
                                        viewModel.toggleLocalAvailability(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = LiveGreen,
                                        checkedTrackColor = LiveGreen.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.deleteProfile() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DoNotDisturb, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Deactivate Registry")
                                }
                            }
                        }
                    }
                }
            } else {
                // New Registration Interactive Questionnaire Layout
                val isDarkTheme = isSystemInDarkTheme()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Register as an Active Area Donor",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Your telemetry data is stored safely locally in SQLite and matched securely against incoming broadcasts, protecting privacy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = regName,
                            onValueChange = { regName = it },
                            label = { Text("Donor Name") },
                            placeholder = { Text("e.g. John Doe") },
                            modifier = Modifier.fillMaxWidth().testTag("donor_name_input"),
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = regContact,
                            onValueChange = { regContact = it },
                            label = { Text("Contact Number") },
                            placeholder = { Text("e.g. +1 555-0100") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth().testTag("donor_contact_input"),
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            singleLine = true
                        )

                        // Exposed dropdown for blood group selector
                        ExposedDropdownMenuBox(
                            expanded = expandedGroupDropdown,
                            onExpandedChange = { expandedGroupDropdown = !expandedGroupDropdown },
                            modifier = Modifier.fillMaxWidth().testTag("donor_blood_dropdown")
                        ) {
                            OutlinedTextField(
                                value = "Blood Group Selection: $regBloodGroup",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedGroupDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedGroupDropdown,
                                onDismissRequest = { expandedGroupDropdown = false }
                            ) {
                                bloodGroupsList.forEach { grp ->
                                    DropdownMenuItem(
                                        text = { Text(grp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            regBloodGroup = grp
                                            expandedGroupDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Availability switch inside form
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to Donate Instantly?",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Checked triggers notification matching inside the 10 km boundaries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDarkTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF475569)
                                )
                            }
                            Checkbox(
                                checked = regIsAvailable,
                                onCheckedChange = { regIsAvailable = it },
                                modifier = Modifier.testTag("donor_availability_checkbox")
                            )
                        }

                        Button(
                            onClick = {
                                if (regName.isBlank() || regContact.isBlank()) {
                                    viewModel.showToast("All fields are mandatory.")
                                    return@Button
                                }
                                viewModel.registerDonor(
                                    name = regName,
                                    bloodGroup = regBloodGroup,
                                    contact = regContact,
                                    isAvailable = regIsAvailable
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("donor_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Registration Profile", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: CREATE EMERGENCY ALERT (REQUEST)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEmergencyAlertTab(viewModel: BloodDonationViewModel) {
    var reqBloodGroup by remember { mutableStateOf("O-") }
    var reqLocationLabel by remember { mutableStateOf("") }
    var reqContact by remember { mutableStateOf("") }
    var reqMessageText by remember { mutableStateOf("") }

    var expandedReqBloodDropdown by remember { mutableStateOf(false) }
    val bloodGroupsList = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    val isDarkTheme = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "⚠️ EMERGENCY BLOOD REQUEST",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isDarkTheme) Color(0xFF1E212A).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.72f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Broadcast Location-Based Alert",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Broadcasting will automatically calculate distance and alert available matched blood type donors within a 10 km target range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color(0xFF475569)
                )

                // Blood type selection exposed dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedReqBloodDropdown,
                    onExpandedChange = { expandedReqBloodDropdown = !expandedReqBloodDropdown },
                    modifier = Modifier.fillMaxWidth().testTag("req_blood_dropdown")
                ) {
                    OutlinedTextField(
                        value = "Critical Required Blood: $reqBloodGroup",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedReqBloodDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedReqBloodDropdown,
                        onDismissRequest = { expandedReqBloodDropdown = false }
                    ) {
                        bloodGroupsList.forEach { grp ->
                            DropdownMenuItem(
                                text = { Text(grp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    reqBloodGroup = grp
                                    expandedReqBloodDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reqLocationLabel,
                    onValueChange = { reqLocationLabel = it },
                    label = { Text("Incident Location / Facility Label") },
                    placeholder = { Text("e.g. Hope Hospital ER Room 4") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("req_location_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = reqContact,
                    onValueChange = { reqContact = it },
                    label = { Text("Urgent Contact Mobile Phone") },
                    placeholder = { Text("e.g. +1 555-9111") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("req_contact_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = reqMessageText,
                    onValueChange = { reqMessageText = it },
                    label = { Text("Critical Case Details / Messages") },
                    placeholder = { Text("e.g. High blood loss clinical labor. Need matched units asap.") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .testTag("req_message_input"),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (reqLocationLabel.isBlank() || reqContact.isBlank()) {
                            viewModel.showToast("Location label and contact details are required.")
                            return@Button
                        }
                        viewModel.broadcastEmergencyAlert(
                            bloodGroup = reqBloodGroup,
                            contact = reqContact,
                            locationLabel = reqLocationLabel,
                            description = reqMessageText
                        )
                        // Clear active fields
                        reqLocationLabel = ""
                        reqContact = ""
                        reqMessageText = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("req_send_alert_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("⚠️ BROADCAST EMERGENCY ALERT", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
