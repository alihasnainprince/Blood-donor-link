package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BloodDatabaseRepository
import com.example.data.Donor
import com.example.data.EmergencyAlert
import com.example.util.LocationUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BloodDonationViewModel(
    application: Application,
    private val repository: BloodDatabaseRepository
) : AndroidViewModel(application) {

    // Device current Location state (Defaults to a Central New York latitude/longitude)
    private val _currentLat = MutableStateFlow(40.7128)
    val currentLat: StateFlow<Double> = _currentLat.asStateFlow()

    private val _currentLon = MutableStateFlow(-74.0060)
    val currentLon: StateFlow<Double> = _currentLon.asStateFlow()

    private val _isGpsSimulationActive = MutableStateFlow(true)
    val isGpsSimulationActive: StateFlow<Boolean> = _isGpsSimulationActive.asStateFlow()

    private val _selectedLocationPresetName = MutableStateFlow("Downtown Medical Center")
    val selectedLocationPresetName: StateFlow<String> = _selectedLocationPresetName.asStateFlow()

    // Database Flows
    val selfProfile: StateFlow<Donor?> = repository.selfProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val otherDonors: StateFlow<List<Donor>> = repository.otherDonors
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val emergencyAlerts: StateFlow<List<EmergencyAlert>> = repository.emergencyAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks which alert was already notified during the app session to prevent spamming popups
    private val notifiedAlertIds = mutableSetOf<Long>()

    // Exposed dynamic match list for the registered user
    val activeMatchAlert: StateFlow<EmergencyAlert?> = combine(
        selfProfile,
        emergencyAlerts,
        _currentLat,
        _currentLon
    ) { profile, alerts, lat, lon ->
        if (profile != null && profile.isAvailable) {
            val userBlood = profile.bloodGroup
            // Match alerts asking for user's blood group and within a 10 km radius
            val matches = alerts.filter { alert ->
                val bloodMatch = alert.bloodGroup.equals(userBlood, ignoreCase = true)
                val dist = LocationUtils.calculateDistance(lat, lon, alert.latitude, alert.longitude)
                bloodMatch && dist <= 10.0
            }
            // Return first match that was not dismissed, prioritizing the newest
            matches.firstOrNull { !notifiedAlertIds.contains(it.id) }
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Form inputs and UI States
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        // Build initial seed mock data on launch so the map and registry are not dry.
        viewModelScope.launch {
            if (repository.otherDonors.first().isEmpty() && repository.selfProfile.first() == null) {
                repository.seedMockData(_currentLat.value, _currentLon.value)
            }
        }
    }

    fun dismissAlertPopup(alertId: Long) {
        notifiedAlertIds.add(alertId)
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    // Update GPS simulation preset coordinates
    fun updateLocationPreset(name: String, lat: Double, lon: Double) {
        _selectedLocationPresetName.value = name
        _currentLat.value = lat
        _currentLon.value = lon
        showToast("Location set to: $name")

        // Update local user coordinates if profile exists
        viewModelScope.launch {
            repository.updateSelfCoordinates(lat, lon)
        }
    }

    fun updateCoordinatesManual(lat: Double, lon: Double) {
        _selectedLocationPresetName.value = "Custom Coordinates"
        _currentLat.value = lat
        _currentLon.value = lon

        // Update local user coordinates if profile exists
        viewModelScope.launch {
            repository.updateSelfCoordinates(lat, lon)
        }
    }

    fun toggleSimulationMode(active: Boolean) {
        _isGpsSimulationActive.value = active
    }

    // Core Actions
    fun registerDonor(name: String, bloodGroup: String, contact: String, isAvailable: Boolean) {
        viewModelScope.launch {
            repository.saveSelfProfile(
                name = name,
                bloodGroup = bloodGroup,
                contact = contact,
                isAvailable = isAvailable,
                lat = _currentLat.value,
                lon = _currentLon.value
            )
            showToast("Successfully registered as active donor!")
        }
    }

    fun deleteProfile() {
        viewModelScope.launch {
            repository.clearSelfProfile()
            showToast("Donor profile successfully deactivated.")
        }
    }

    fun deleteDonor(id: Long) {
        viewModelScope.launch {
            repository.deleteDonor(id)
            showToast("Donor registry entry cleared.")
        }
    }

    fun toggleLocalAvailability(isAvailable: Boolean) {
        viewModelScope.launch {
            repository.updateSelfAvailability(isAvailable)
            showToast(if (isAvailable) "You are now Ready to Donate" else "Availability set to Not Ready")
        }
    }

    fun broadcastEmergencyAlert(bloodGroup: String, contact: String, locationLabel: String, description: String) {
        viewModelScope.launch {
            val alert = EmergencyAlert(
                bloodGroup = bloodGroup,
                locationName = locationLabel,
                latitude = _currentLat.value,
                longitude = _currentLon.value,
                contactNumber = contact,
                message = description,
                timestamp = System.currentTimeMillis()
            )
            repository.insertAlert(alert)
            showToast("EMERGENCY BROADCAST SENT! Matched donors nearby are flagged.")
        }
    }

    fun deleteAlert(id: Long) {
        viewModelScope.launch {
            repository.deleteAlert(id)
            showToast("Emergency request closed.")
        }
    }

    fun resetAndSeed() {
        viewModelScope.launch {
            repository.seedMockData(_currentLat.value, _currentLon.value)
            notifiedAlertIds.clear()
            showToast("Database reset with fresh test records.")
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            repository.clearAllData()
            notifiedAlertIds.clear()
            showToast("Database cleared.")
        }
    }
}

class BloodViewModelFactory(
    private val application: Application,
    private val repository: BloodDatabaseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BloodDonationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BloodDonationViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
