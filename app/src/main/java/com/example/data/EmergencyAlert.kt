package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_alerts")
data class EmergencyAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bloodGroup: String,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val contactNumber: String,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
