package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.BloodDatabaseRepository
import com.example.ui.BloodDonationScreen
import com.example.ui.BloodDonationViewModel
import com.example.ui.BloodViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    // Instantiate Room DB & Repository
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val repository by lazy { BloodDatabaseRepository(database.appDao()) }

    // Use Factory provider to pass repository with correct lifecycles
    private val viewModel: BloodDonationViewModel by viewModels {
        BloodViewModelFactory(application, repository)
    }

    // Modern asynchronous declaration for fine and coarse location permission prompts
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.showToast("GPS location permission granted!")
        } else {
            viewModel.showToast("Location permission denied. Running in simulator mode.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Trigger safe Android location permission approvals on launch
        requestLocationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Main App Coordinate Core
                    BloodDonationScreen(viewModel = viewModel)
                }
            }
        }
    }
}
