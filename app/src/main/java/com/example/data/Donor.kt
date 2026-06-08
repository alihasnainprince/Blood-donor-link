package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "donors")
data class Donor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bloodGroup: String,
    val isAvailable: Boolean,
    val contactNumber: String,
    val latitude: Double,
    val longitude: Double,
    val isLocalSelf: Boolean = false, // To identify if this is the active user's own profile
    val lastUpdated: Long = System.currentTimeMillis()
)
