package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.model.Donor
import com.example.model.EmergencyRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sharedPreferences = getSharedPreferences("blood_donor_pref", Context.MODE_PRIVATE)

        // Generate or fetch user ID
        var userId = sharedPreferences.getString("user_id", null)
        if (userId == null) {
            userId = UUID.randomUUID().toString().take(8)
            sharedPreferences.edit().putString("user_id", userId).apply()
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFFE63946), // Crimson Ruby Red
                    secondary = Color(0xFF1D3557), // Indigo Midnight Steel
                    tertiary = Color(0xFF457B9D), // Cool Slate Blue
                    background = Color(0xFFFFFAFA), // Delicate warm ivory-pink
                    surface = Color.White,
                    onPrimary = Color.White,
                    onBackground = Color(0xFF1D3557),
                    surfaceVariant = Color(0xFFFDF0F0)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BloodDonorAppMain(
                        fusedLocationClient = fusedLocationClient,
                        sharedPreferences = sharedPreferences,
                        currentUserId = userId!!
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodDonorAppMain(
    fusedLocationClient: FusedLocationProviderClient,
    sharedPreferences: SharedPreferences,
    currentUserId: String
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) } // 0: Map, 1: Emergency Requests, 2: My Profile

    // Real-time local state synced with Firestore if available (or in-memory fallback)
    val donorsState = remember { mutableStateListOf<Donor>() }
    val requestsState = remember { mutableStateListOf<EmergencyRequest>() }

    // User's current location state
    var userLocation by remember { mutableStateOf(LatLng(23.6850, 90.3563)) } // Bangladesh default coords
    var locationPermissionGranted by remember { mutableStateOf(false) }

    // Selected blood group filter for the Map View
    var selectedGroupFilter by remember { mutableStateOf("All") }

    // My Profile profile state loaded from preferences / Firestore
    var myProfileName by remember { mutableStateOf(sharedPreferences.getString("donor_name", "") ?: "") }
    var myProfileBloodGroup by remember { mutableStateOf(sharedPreferences.getString("donor_bg", "O+") ?: "O+") }
    var myProfilePhone by remember { mutableStateOf(sharedPreferences.getString("donor_phone", "") ?: "") }
    var myProfileIsAvailable by remember { mutableStateOf(sharedPreferences.getBoolean("donor_avail", false) ?: false) }
    var hasRegisteredProfile by remember { mutableStateOf(sharedPreferences.getBoolean("donor_registered", false)) }
    var myProfileNameTouched by remember { mutableStateOf(false) }
    var myProfilePhoneTouched by remember { mutableStateOf(false) }
    var showProfileValidationErrors by remember { mutableStateOf(false) }

    // Simulation states
    var simulationActive by remember { mutableStateOf(true) }

    // Forms and Dialog controllers
    var showAddRequestDialog by remember { mutableStateOf(false) }
    var selectedMarkerDetails by remember { mutableStateOf<Any?>(null) } // Can be Donor or EmergencyRequest

    // Initialize Firebase Firestore safe handler
    val db = remember {
        try {
            FirebaseApp.initializeApp(context)
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    // Initialize Simulation Data
    LaunchedEffect(simulationActive) {
        if (simulationActive) {
            // Seed mock local state to make the interface visual and immediate
            val mockDonors = listOf(
                Donor("sim_1", "Dr. Arif Rahman", "A+", 23.8103, 90.4125, true, "+8801712345678"),
                Donor("sim_2", "Tasnim Ahmed", "O+", 23.7561, 90.3762, true, "+8801811223344"),
                Donor("sim_3", "Sajib Hasan", "B-", 23.7940, 90.4042, false, "+8801911998877"),
                Donor("sim_4", "Fahmida Chowdhury", "AB+", 23.7461, 90.3860, true, "+8801511223355"),
                Donor("sim_5", "Kamrul Islam", "O-", 23.7250, 90.4000, true, "+8801612345612"),
                Donor("sim_6", "Sadia Sultana", "B+", 23.8012, 90.3582, true, "+8801700112233")
            )
            val mockRequests = listOf(
                EmergencyRequest(
                    "req_1", "Mrs. Jahanara", "O+", "Dhaka Medical College", 23.7258, 90.3972,
                    "+8801733445566", 2, "Urgent heart surgery. Needs donors today."
                ),
                EmergencyRequest(
                    "req_2", "Ratul Sen", "A+", "Evercare Hospital Dhaka", 23.8115, 90.4312,
                    "+8801922334455", 1, "Thalassemia patient regular transfusion."
                ),
                EmergencyRequest(
                    "req_3", "Mahi Al-Hasan", "AB-", "Apollo Clinic Chittagong", 22.3569, 91.7832,
                    "+8801822336699", 3, "Accident victim with major blood loss. Emergency!"
                )
            )

            // Merge if they are not already in state
            mockDonors.forEach { sub ->
                if (donorsState.none { it.id == sub.id }) donorsState.add(sub)
            }
            mockRequests.forEach { sub ->
                if (requestsState.none { it.id == sub.id }) requestsState.add(sub)
            }

            // Sync with Firestore if available
            db?.let { firestore ->
                for (mockDonor in mockDonors) {
                    firestore.collection("donors").document(mockDonor.id).set(mockDonor)
                }
                for (mockReq in mockRequests) {
                    firestore.collection("emergency_requests").document(mockReq.id).set(mockReq)
                }
            }
        }
    }

    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationPermissionGranted = granted
    }

    // Request Location of Device
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            locationPermissionGranted = true
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Continuous Live Tracking of Self User
    LaunchedEffect(locationPermissionGranted) {
        if (locationPermissionGranted) {
            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000L).apply {
                    setMinUpdateIntervalMillis(10000L)
                }.build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { loc ->
                            userLocation = LatLng(loc.latitude, loc.longitude)

                            // If donor is registered and logged in, update my position dynamically on storage
                            if (hasRegisteredProfile) {
                                val updatedProfile = Donor(
                                    id = currentUserId,
                                    name = myProfileName,
                                    bloodGroup = myProfileBloodGroup,
                                    latitude = loc.latitude,
                                    longitude = loc.longitude,
                                    isAvailable = myProfileIsAvailable,
                                    phone = myProfilePhone,
                                    lastUpdated = System.currentTimeMillis()
                                )
                                // Save local cache and update Firestore
                                donorsState.removeAll { it.id == currentUserId }
                                donorsState.add(updatedProfile)

                                db?.collection("donors")?.document(currentUserId)?.set(updatedProfile)
                            }
                        }
                    }
                }
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                // Squelch permission issues
            }
        }
    }

    // Firestore Observers
    DisposableEffect(db) {
        var donorsListener: ListenerRegistration? = null
        var requestsListener: ListenerRegistration? = null

        if (db != null) {
            // Observe Donors
            donorsListener = db.collection("donors")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        for (doc in snapshot.documents) {
                            val d = doc.toObject(Donor::class.java)
                            if (d != null) {
                                donorsState.removeAll { it.id == d.id }
                                donorsState.add(d)
                            }
                        }
                    }
                }

            // Observe Requests
            requestsListener = db.collection("emergency_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        for (doc in snapshot.documents) {
                            val r = doc.toObject(EmergencyRequest::class.java)
                            if (r != null) {
                                requestsState.removeAll { it.id == r.id }
                                requestsState.add(r)
                            }
                        }
                    }
                }
        }

        onDispose {
            donorsListener?.remove()
            requestsListener?.remove()
        }
    }

    // Scaffold holding everything
    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFE63946), // Primary Crimson
                                Color(0xFFC32A36)  // Deeper Cherry Red
                            )
                        )
                    )
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.18f), CircleShape)
                                    .border(1.2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "BloodLink",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = if (simulationActive) "Demo & Live Sync Enabled" else "Firestore Connected",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        // Simulation toggle badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                .clickable {
                                    simulationActive = !simulationActive
                                    Toast
                                        .makeText(
                                            context,
                                            if (simulationActive) "Simulation mode activated" else "Simulation mode deactivated",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (simulationActive) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Simulation",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = (currentTab == 0),
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Map, "Map View") },
                    label = { Text("Map View", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_map")
                )
                NavigationBarItem(
                    selected = (currentTab == 1),
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Announcement, "Emergency Feed") },
                    label = { Text("Feed", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_feed")
                )
                NavigationBarItem(
                    selected = (currentTab == 2),
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Person, "My Profile") },
                    label = { Text("My Profile", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("nav_profile")
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 1) {
                ExtendedFloatingActionButton(
                    text = { Text("Post Request", color = Color.White) },
                    icon = { Icon(Icons.Default.AddAlert, "Post Alert", tint = Color.White) },
                    onClick = { showAddRequestDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .testTag("fab_post_request")
                        .padding(bottom = 16.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> {
                    // Map View Screen
                    MapScreen(
                        donors = donorsState,
                        requests = requestsState,
                        userLocation = userLocation,
                        selectedGroupFilter = selectedGroupFilter,
                        onFilterChange = { selectedGroupFilter = it },
                        onMarkerClick = { selectedMarkerDetails = it }
                    )
                }
                1 -> {
                    // Emergency Feed List
                    EmergencyFeedScreen(
                        requests = requestsState,
                        onCallProvider = { phone ->
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        },
                        onNavigateToRequest = { req ->
                            // Recenter map on request coords and switch tab
                            userLocation = LatLng(req.latitude, req.longitude)
                            currentTab = 0
                            selectedMarkerDetails = req
                        }
                    )
                }
                2 -> {
                    // My Profile Dashboard & Enrollment
                    // Calculate validation error strings dynamically
                    val profileNameError = if ((myProfileNameTouched || showProfileValidationErrors) && !FormValidator.isValidName(myProfileName)) {
                        "Enter a valid name (at least 2 letters, no special characters)"
                    } else null

                    val profilePhoneError = if ((myProfilePhoneTouched || showProfileValidationErrors) && !FormValidator.isValidPhoneNumber(myProfilePhone)) {
                        "Enter a valid phone number (7 to 15 digits)"
                    } else null

                    val profileBloodGroupError = if (showProfileValidationErrors && !FormValidator.isValidBloodGroup(myProfileBloodGroup)) {
                        "Please select a recognized blood group type"
                    } else null

                    MyProfileScreen(
                        currentUserId = currentUserId,
                        hasProfile = hasRegisteredProfile,
                        name = myProfileName,
                        onNameChange = { 
                            myProfileName = it 
                            myProfileNameTouched = true
                        },
                        bloodGroup = myProfileBloodGroup,
                        onBloodGroupChange = { myProfileBloodGroup = it },
                        phone = myProfilePhone,
                        onPhoneChange = { 
                            myProfilePhone = it 
                            myProfilePhoneTouched = true
                        },
                        isAvailable = myProfileIsAvailable,
                        onAvailabilityChange = { myProfileIsAvailable = it },
                        nameError = profileNameError,
                        phoneError = profilePhoneError,
                        bloodGroupError = profileBloodGroupError,
                        onRegister = {
                            myProfileNameTouched = true
                            myProfilePhoneTouched = true
                            val nameValid = FormValidator.isValidName(myProfileName)
                            val phoneValid = FormValidator.isValidPhoneNumber(myProfilePhone)
                            val bgValid = FormValidator.isValidBloodGroup(myProfileBloodGroup)

                            if (!nameValid || !phoneValid || !bgValid) {
                                showProfileValidationErrors = true
                                val firstErrorMsg = when {
                                    !nameValid -> "Check that your profile name is typed correctly with no numbers."
                                    !phoneValid -> "Enter a valid phone number (7-15 digits with optional country prefix)."
                                    !bgValid -> "Please select a recognized blood group type."
                                    else -> "Please correct highlighted profile fields."
                                }
                                Toast.makeText(context, firstErrorMsg, Toast.LENGTH_LONG).show()
                            } else {
                                hasRegisteredProfile = true
                                sharedPreferences.edit().apply {
                                    putString("donor_name", myProfileName.trim())
                                    putString("donor_bg", myProfileBloodGroup)
                                    putString("donor_phone", myProfilePhone.trim())
                                    putBoolean("donor_avail", myProfileIsAvailable)
                                    putBoolean("donor_registered", true)
                                    apply()
                                }

                                // Update Firestore and cached state object
                                val profile = Donor(
                                    id = currentUserId,
                                    name = myProfileName.trim(),
                                    bloodGroup = myProfileBloodGroup,
                                    latitude = userLocation.latitude,
                                    longitude = userLocation.longitude,
                                    isAvailable = myProfileIsAvailable,
                                    phone = myProfilePhone.trim(),
                                    lastUpdated = System.currentTimeMillis()
                                )
                                donorsState.removeAll { it.id == currentUserId }
                                donorsState.add(profile)

                                db?.collection("donors")?.document(currentUserId)?.set(profile)
                                Toast.makeText(context, "Successfully registered as an Active Blood Donor!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeleteProfile = {
                            hasRegisteredProfile = false
                            sharedPreferences.edit().clear().apply()
                            myProfileName = ""
                            myProfilePhone = ""
                            myProfileIsAvailable = false
                            myProfileNameTouched = false
                            myProfilePhoneTouched = false
                            showProfileValidationErrors = false

                            donorsState.removeAll { it.id == currentUserId }
                            db?.collection("donors")?.document(currentUserId)?.delete()
                            Toast.makeText(context, "Removed your donor profile.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Bottom Sheets / Dialog overlays
            if (showAddRequestDialog) {
                AddEmergencyRequestDialog(
                    userLocation = userLocation,
                    onDismiss = { showAddRequestDialog = false },
                    onPostRequest = { newReq ->
                        val requestWithId = newReq.copy(id = UUID.randomUUID().toString())
                        requestsState.add(requestWithId)
                        db?.collection("emergency_requests")?.document(requestWithId.id)?.set(requestWithId)
                        showAddRequestDialog = false
                        Toast.makeText(context, "Posted Urgent Blood Appeal Category!", Toast.LENGTH_LONG).show()
                    }
                )
            }

            if (selectedMarkerDetails != null) {
                MarkerDetailDialog(
                    details = selectedMarkerDetails!!,
                    onDismiss = { selectedMarkerDetails = null },
                    onCall = { phone ->
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
fun MapScreen(
    donors: List<Donor>,
    requests: List<EmergencyRequest>,
    userLocation: LatLng,
    selectedGroupFilter: String,
    onFilterChange: (String) -> Unit,
    onMarkerClick: (Any) -> Unit
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(userLocation, 12f)
    }

    // Reacting to location adjustment to center camera frame
    LaunchedEffect(userLocation) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(userLocation, 12f)
    }

    val bloodGroupsList = listOf("All", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    Box(modifier = Modifier.fillMaxSize()) {
        // Real interactive map component
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false)
        ) {
            // User Location marker
            Marker(
                state = MarkerState(position = userLocation),
                title = "Your Current Location",
                snippet = "Accurate GPS sync position",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
            )

            // Live donors markers
            donors.filter { selectedGroupFilter == "All" || it.bloodGroup == selectedGroupFilter }.forEach { d ->
                val tintColor = if (d.isAvailable) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_RED
                Marker(
                    state = MarkerState(position = LatLng(d.latitude, d.longitude)),
                    title = "Donor: ${d.name} (${d.bloodGroup})",
                    snippet = "Phone: ${d.phone} | tap for details",
                    icon = BitmapDescriptorFactory.defaultMarker(tintColor),
                    onClick = {
                        onMarkerClick(d)
                        true
                    }
                )
            }

            // Live urgencies markers
            requests.filter { selectedGroupFilter == "All" || it.bloodGroup == selectedGroupFilter }.forEach { r ->
                Marker(
                    state = MarkerState(position = LatLng(r.latitude, r.longitude)),
                    title = "URGENT [${r.bloodGroup}] Need",
                    snippet = "Hospital: ${r.hospitalName} | tap for details",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE),
                    onClick = {
                        onMarkerClick(r)
                        true
                    }
                )
            }
        }

        // Floating premium horizontal filter card bar at the top of the Map view
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.94f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(bloodGroupsList) { bg ->
                        val isSelected = (bg == selectedGroupFilter)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFFFFAFA)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else Color.LightGray.copy(alpha = 0.6f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onFilterChange(bg) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("filter_chip_$bg"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = bg,
                                color = if (isSelected) Color.White else Color.DarkGray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyFeedScreen(
    requests: List<EmergencyRequest>,
    onCallProvider: (String) -> Unit,
    onNavigateToRequest: (EmergencyRequest) -> Unit
) {
    val totalRequests = requests.size
    val totalUnits = requests.sumOf { it.unitsNeeded }

    if (requests.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Inbox empty logo",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "No Current Blood Appeals",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "If there are urgent requests for trans-fusing blood in hospitals nearby, they will be listed here dynamically.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // High-fidelity Stats Card Header
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        Color(0xFFC32A36)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Blood Appeals Feed",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    letterSpacing = (-0.5).sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "LIVE UPDATE",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "People in local healthcare hubs require immediate support. Tap 'Call Now' to coordinate and save a life.",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Stat card 1
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Campaign,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "$totalRequests",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Appeals",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Stat card 2
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bloodtype,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "$totalUnits Bags",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "Requested",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            items(requests.sortedByDescending { it.timestamp }) { req ->
                val isUrgent = req.unitsNeeded >= 2
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_card_${req.id}"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, if (isUrgent) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.LightGray.copy(alpha = 0.4f))
                ) {
                    Column {
                        // High-contrast priority accent banner inside the card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isUrgent) Color(0xFFFFF1F1) else Color(0xFFF7F9FC))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(if (isUrgent) Color(0xFFE63946) else Color(0xFF457B9D), CircleShape)
                                    )
                                    Text(
                                        text = if (isUrgent) "CRITICAL PRIORITY" else "STANDARD APPEAL",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        color = if (isUrgent) Color(0xFFE63946) else Color(0xFF457B9D),
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "Needs ${req.unitsNeeded} Bag(s)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUrgent) Color(0xFFE63946) else Color.DarkGray
                                )
                            }
                        }

                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.patientName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = req.hospitalName,
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                
                                // Beautiful floating Blood Group badge
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(Color(0xFFE63946), Color(0xFFC32A36))
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = req.bloodGroup,
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            if (req.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = req.description,
                                    fontSize = 14.sp,
                                    color = Color.DarkGray,
                                    lineHeight = 18.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onNavigateToRequest(req) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, "Map", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Map Location", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onCallProvider(req.phone) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Phone, "Call", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Call Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyProfileScreen(
    currentUserId: String,
    hasProfile: Boolean,
    name: String,
    onNameChange: (String) -> Unit,
    bloodGroup: String,
    onBloodGroupChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    isAvailable: Boolean,
    onAvailabilityChange: (Boolean) -> Unit,
    nameError: String? = null,
    phoneError: String? = null,
    bloodGroupError: String? = null,
    onRegister: () -> Unit,
    onDeleteProfile: () -> Unit
) {
    val bGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            if (hasProfile) {
                // High-fidelity active status badge
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    border = BorderStroke(1.2.dp, Color(0xFF81C784).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Savior Badge",
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "STATUS: ACTIVE EMERGENCY SAVIOR",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp,
                                color = Color(0xFF2E7D32),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Registry Online & Synced",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1B5E20)
                            )
                            Text(
                                text = "Your profile is searchable on live maps.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .background(Color(0xFFFFF1F1), CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bloodtype,
                        contentDescription = "Profile blood indicator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasProfile) "Manage Your Savior Profile" else "Join as a Life Saver",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasProfile) "Keep your contact info and visibility status up to date to guarantee life-saving responses." else "Registered users become instantly searchable on the live map in case of medical emergencies.",
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Donor Profile Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = { Text("Your Complete Name") },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input"),
                        leadingIcon = { Icon(Icons.Default.Badge, "Name badge") },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = onPhoneChange,
                        label = { Text("Contact Phone Line") },
                        isError = phoneError != null,
                        supportingText = phoneError?.let { { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_phone_input"),
                        leadingIcon = { Icon(Icons.Default.Phone, "Phone banner") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )

                    // Blood Group selection label
                    Column {
                        Text(
                            text = "Select Blood Group Type",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.DarkGray
                        )
                        if (bloodGroupError != null) {
                            Text(
                                text = bloodGroupError,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(bGroups) { bg ->
                                val selected = (bg == bloodGroup)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary else Color(
                                                0xFFEEEEEE
                                            )
                                        )
                                        .clickable { onBloodGroupChange(bg) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = bg,
                                        color = if (selected) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Available to Donate?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Disable matching visibility when you cannot donate.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isAvailable,
                            onCheckedChange = onAvailabilityChange,
                            modifier = Modifier.testTag("profile_available_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AnimatedVisibility(visible = !hasProfile) {
                        Button(
                            onClick = onRegister,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_register"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Enroll as Registered Donor", fontWeight = FontWeight.Bold)
                        }
                    }

                    AnimatedVisibility(visible = hasProfile) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRegister,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.DarkGray
                                )
                            ) {
                                Text("Update Registry Profile Information", fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(
                                onClick = onDeleteProfile,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Delete My Profile & Stop Enrolling", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddEmergencyRequestDialog(
    userLocation: LatLng,
    onDismiss: () -> Unit,
    onPostRequest: (EmergencyRequest) -> Unit
) {
    var patientName by remember { mutableStateOf("") }
    var hospitalName by remember { mutableStateOf("") }
    var selectedBg by remember { mutableStateOf("O+") }
    var contactPhone by remember { mutableStateOf("") }
    var unitsNeeded by remember { mutableStateOf("1") }
    var patientDetailsDescription by remember { mutableStateOf("") }

    var showErrors by remember { mutableStateOf(false) }
    var patientNameTouched by remember { mutableStateOf(false) }
    var hospitalNameTouched by remember { mutableStateOf(false) }
    var contactPhoneTouched by remember { mutableStateOf(false) }
    var unitsTouched by remember { mutableStateOf(false) }

    val isPatientNameValid = FormValidator.isValidName(patientName)
    val isHospitalNameValid = hospitalName.isNotBlank() && hospitalName.trim().length >= 3
    val isPhoneValid = FormValidator.isValidPhoneNumber(contactPhone)
    val isBgValid = FormValidator.isValidBloodGroup(selectedBg)
    val unitsInt = unitsNeeded.toIntOrNull()
    val isUnitsValid = unitsInt != null && unitsInt > 0 && unitsInt <= 20

    val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Request Emergency Blood",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = patientName,
                    onValueChange = { 
                        patientName = it 
                        patientNameTouched = true
                    },
                    label = { Text("Patient Name") },
                    isError = (patientNameTouched || showErrors) && !isPatientNameValid,
                    supportingText = if ((patientNameTouched || showErrors) && !isPatientNameValid) {
                        { Text("Enter a valid name (letters only, min 2 chars)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("req_patient_name_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = hospitalName,
                    onValueChange = { 
                        hospitalName = it 
                        hospitalNameTouched = true
                    },
                    label = { Text("Hospital Name (Location)") },
                    isError = (hospitalNameTouched || showErrors) && !isHospitalNameValid,
                    supportingText = if ((hospitalNameTouched || showErrors) && !isHospitalNameValid) {
                        { Text("Hospital location is required (min 3 chars)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("req_hospital_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { 
                        contactPhone = it 
                        contactPhoneTouched = true
                    },
                    label = { Text("Contact Phone") },
                    isError = (contactPhoneTouched || showErrors) && !isPhoneValid,
                    supportingText = if ((contactPhoneTouched || showErrors) && !isPhoneValid) {
                        { Text("Enter a valid contact number (7-15 digits)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("req_phone_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                OutlinedTextField(
                    value = unitsNeeded,
                    onValueChange = { 
                        unitsNeeded = it 
                        unitsTouched = true
                    },
                    label = { Text("Units of Blood Needed") },
                    isError = (unitsTouched || showErrors) && !isUnitsValid,
                    supportingText = if ((unitsTouched || showErrors) && !isUnitsValid) {
                        { Text("Enter a valid request amount (1 to 20 units)", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                    } else null,
                    modifier = Modifier.fillMaxWidth().testTag("req_units_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Text(
                    text = "Selected Blood Group Required:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(bloodGroups) { bg ->
                        val isSel = (bg == selectedBg)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSel) MaterialTheme.colorScheme.primary else Color(
                                        0xFFF5F5F5
                                    )
                                )
                                .clickable { selectedBg = bg }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = bg,
                                color = if (isSel) Color.White else Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = patientDetailsDescription,
                    onValueChange = { patientDetailsDescription = it },
                    label = { Text("Brief Description of Urgency") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            patientNameTouched = true
                            hospitalNameTouched = true
                            contactPhoneTouched = true
                            unitsTouched = true

                            if (isPatientNameValid && isHospitalNameValid && isPhoneValid && isBgValid && isUnitsValid) {
                                val reqObj = EmergencyRequest(
                                    uuid = UUID.randomUUID().toString(),
                                    patientName = patientName.trim(),
                                    bloodGroup = selectedBg,
                                    hospitalName = hospitalName.trim(),
                                    latitude = userLocation.latitude + (Math.random() - 0.5) * 0.02, // slight jitter to mock hospital area
                                    longitude = userLocation.longitude + (Math.random() - 0.5) * 0.02,
                                    phone = contactPhone.trim(),
                                    unitsNeeded = unitsInt ?: 1,
                                    description = patientDetailsDescription,
                                    timestamp = System.currentTimeMillis()
                                )
                                onPostRequest(reqObj)
                            } else {
                                showErrors = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Post Broadcast", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Compact helper extending EmergencyRequest mapping parameters matching design
private fun EmergencyRequest(
    uuid: String,
    patientName: String,
    bloodGroup: String,
    hospitalName: String,
    latitude: Double,
    longitude: Double,
    phone: String,
    unitsNeeded: Int,
    description: String,
    timestamp: Long
) = EmergencyRequest(
    id = uuid,
    patientName = patientName,
    bloodGroup = bloodGroup,
    hospitalName = hospitalName,
    latitude = latitude,
    longitude = longitude,
    phone = phone,
    unitsNeeded = unitsNeeded,
    description = description,
    timestamp = timestamp,
    active = true
)

@Composable
fun MarkerDetailDialog(
    details: Any,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (details is Donor) {
                    Text(
                        text = "Blood Donor Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF2E7D32) // green theme
                    )
                    Divider()
                    Text(text = "Name: ${details.name}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(text = "Blood Type: ${details.bloodGroup}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Red)
                    Text(text = "Phone Contact: ${details.phone}", fontSize = 14.sp)
                    Text(
                        text = "Availability Status: " + if (details.isAvailable) "Active Saviors" else "Offline Cooldown",
                        fontWeight = FontWeight.Bold,
                        color = if (details.isAvailable) Color(0xFF2E7D32) else Color.Red
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onCall(details.phone) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Default.Phone, "Call", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Call Saviors")
                        }
                    }
                } else if (details is EmergencyRequest) {
                    Text(
                        text = "Emergency Blood Broadcast",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Divider()
                    Text(text = "Patient: ${details.patientName}", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(text = "Blood Group Required: ${details.bloodGroup}", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    Text(text = "Hospital: ${details.hospitalName}", fontSize = 14.sp)
                    Text(text = "Units Needed: ${details.unitsNeeded} unit(s)", fontSize = 14.sp)
                    
                    if (details.description.isNotEmpty()) {
                        Text(
                            text = "Brief: ${details.description}",
                            fontSize = 13.sp,
                            color = Color.DarkGray
                        )
                    }

                    val dateFormatted = SimpleDateFormat("HH:mm, dd MMM yyyy", Locale.getDefault()).format(Date(details.timestamp))
                    Text(text = "Broadcasted At: $dateFormatted", fontSize = 11.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Close") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onCall(details.phone) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Phone, "Call", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect Now")
                        }
                    }
                }
            }
        }
    }
}

object FormValidator {
    val VALID_BLOOD_GROUPS = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")

    fun isValidBloodGroup(group: String): Boolean {
        return VALID_BLOOD_GROUPS.contains(group.trim().uppercase())
    }

    fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        if (cleaned.isEmpty()) return false
        val regex = Regex("^\\+?[0-9]{7,15}$")
        return cleaned.matches(regex)
    }

    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < 2) return false
        return !trimmed.any { it.isDigit() || "@$%&*()[]{}|<>?/\\+=".contains(it) }
    }
}
