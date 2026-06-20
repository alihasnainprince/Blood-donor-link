package com.example.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class EmergencyRequest(
    val id: String = "",
    val patientName: String = "",
    val bloodGroup: String = "",
    val hospitalName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val phone: String = "",
    val unitsNeeded: Int = 1,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val active: Boolean = true
)
