package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Self Profile Queries
    @Query("SELECT * FROM donors WHERE isLocalSelf = 1 LIMIT 1")
    fun getSelfProfileFlow(): Flow<Donor?>

    @Query("SELECT * FROM donors WHERE isLocalSelf = 1 LIMIT 1")
    suspend fun getSelfProfile(): Donor?

    // General Donor Queries
    @Query("SELECT * FROM donors WHERE isLocalSelf = 0 ORDER BY lastUpdated DESC")
    fun getOtherDonorsFlow(): Flow<List<Donor>>

    @Query("SELECT * FROM donors WHERE isAvailable = 1 AND isLocalSelf = 0")
    suspend fun getActiveOtherDonors(): List<Donor>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDonor(donor: Donor)

    @Update
    suspend fun updateDonor(donor: Donor)

    @Query("DELETE FROM donors WHERE id = :id")
    suspend fun deleteDonorById(id: Long)

    @Query("DELETE FROM donors WHERE isLocalSelf = 1")
    suspend fun deleteSelfProfile()

    // Emergency Alert Queries
    @Query("SELECT * FROM emergency_alerts ORDER BY timestamp DESC")
    fun getAllAlertsFlow(): Flow<List<EmergencyAlert>>

    @Query("SELECT * FROM emergency_alerts ORDER BY timestamp DESC")
    suspend fun getAllAlerts(): List<EmergencyAlert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: EmergencyAlert)

    @Query("DELETE FROM emergency_alerts WHERE id = :id")
    suspend fun deleteAlertById(id: Long)

    // Clear everything for resets / seeding
    @Query("DELETE FROM donors")
    suspend fun clearDonors()

    @Query("DELETE FROM emergency_alerts")
    suspend fun clearAlerts()
}
