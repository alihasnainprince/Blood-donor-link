package com.example.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class BloodDatabaseRepository(private val appDao: AppDao) {

    val selfProfile: Flow<Donor?> = appDao.getSelfProfileFlow()
    val otherDonors: Flow<List<Donor>> = appDao.getOtherDonorsFlow()
    val emergencyAlerts: Flow<List<EmergencyAlert>> = appDao.getAllAlertsFlow()

    suspend fun getSelfProfileDirect(): Donor? = appDao.getSelfProfile()

    suspend fun saveSelfProfile(name: String, bloodGroup: String, contact: String, isAvailable: Boolean, lat: Double, lon: Double) {
        val existing = appDao.getSelfProfile()
        val profile = Donor(
            id = existing?.id ?: 0,
            name = name,
            bloodGroup = bloodGroup,
            contactNumber = contact,
            isAvailable = isAvailable,
            latitude = lat,
            longitude = lon,
            isLocalSelf = true,
            lastUpdated = System.currentTimeMillis()
        )
        appDao.insertDonor(profile)
    }

    suspend fun updateSelfCoordinates(lat: Double, lon: Double) {
        val existing = appDao.getSelfProfile() ?: return
        val updated = existing.copy(
            latitude = lat,
            longitude = lon,
            lastUpdated = System.currentTimeMillis()
        )
        appDao.insertDonor(updated)
    }

    suspend fun updateSelfAvailability(isAvailable: Boolean) {
        val existing = appDao.getSelfProfile() ?: return
        val updated = existing.copy(
            isAvailable = isAvailable,
            lastUpdated = System.currentTimeMillis()
        )
        appDao.insertDonor(updated)
    }

    suspend fun insertDonor(donor: Donor) {
        appDao.insertDonor(donor)
    }

    suspend fun deleteDonor(id: Long) {
        appDao.deleteDonorById(id)
    }

    suspend fun clearSelfProfile() {
        appDao.deleteSelfProfile()
    }

    suspend fun insertAlert(alert: EmergencyAlert) {
        appDao.insertAlert(alert)
    }

    suspend fun deleteAlert(id: Long) {
        appDao.deleteAlertById(id)
    }

    suspend fun clearAllData() {
        appDao.clearDonors()
        appDao.clearAlerts()
    }

    // Seed mock donors relative to a given anchor location to verify distance-based calculations easily
    suspend fun seedMockData(anchorLat: Double, anchorLon: Double) {
        appDao.clearDonors()
        appDao.clearAlerts()

        // Core Blood groups: A+, A-, B+, B-, AB+, AB-, O+, O-
        // Lat/Lon offsets for distance simulation:
        // Math context: 0.01 deg latitude ~ 1.11 km, 0.01 deg longitude ~ 0.9 km (mid latitude)
        val mockDonors = listOf(
            Donor(
                name = "Sarah Jenkins",
                bloodGroup = "O+",
                isAvailable = true,
                contactNumber = "+1 (555) 019-2834",
                latitude = anchorLat + 0.015, // ~1.8 km North-East
                longitude = anchorLon + 0.012,
                isLocalSelf = false
            ),
            Donor(
                name = "David Chen",
                bloodGroup = "A-",
                isAvailable = true,
                contactNumber = "+1 (555) 034-9128",
                latitude = anchorLat - 0.025, // ~3.1 km South-West
                longitude = anchorLon - 0.018,
                isLocalSelf = false
            ),
            Donor(
                name = "Elena Rostova",
                bloodGroup = "B+",
                isAvailable = true,
                contactNumber = "+1 (555) 043-1122",
                latitude = anchorLat + 0.045, // ~5.5 km North-East
                longitude = anchorLon + 0.035,
                isLocalSelf = false
            ),
            Donor(
                name = "Marcus Aurelius",
                bloodGroup = "AB+",
                isAvailable = true,
                contactNumber = "+1 (555) 089-7756",
                latitude = anchorLat - 0.075, // ~9.2 km South-West
                longitude = anchorLon - 0.055,
                isLocalSelf = false
            ),
            Donor(
                name = "Sofia Rodriguez",
                bloodGroup = "O-",
                isAvailable = true,
                contactNumber = "+1 (555) 014-4321",
                latitude = anchorLat + 0.125, // ~14.5 km away (outside 10km filter)
                longitude = anchorLon + 0.095,
                isLocalSelf = false
            ),
            Donor(
                name = "Liam Neeson",
                bloodGroup = "A+",
                isAvailable = false, // Not Available right now
                contactNumber = "+1 (555) 022-8899",
                latitude = anchorLat + 0.020, // ~2.5 km (Close, but unavailable)
                longitude = anchorLon - 0.010,
                isLocalSelf = false
            )
        )

        for (donor in mockDonors) {
            appDao.insertDonor(donor)
        }

        // Add 1 default alert to demonstrate the UI
        val mockAlert = EmergencyAlert(
            bloodGroup = "O-",
            locationName = "City General Hospital",
            latitude = anchorLat + 0.032, // ~4.0 km away
            longitude = anchorLon + 0.021,
            contactNumber = "+1 (555) 911-3000",
            message = "Critical multi-vehicle accident trauma patient requires O- negative blood ASAP.",
            timestamp = System.currentTimeMillis() - 3600000 // 1 hour ago
        )
        appDao.insertAlert(mockAlert)
    }
}
