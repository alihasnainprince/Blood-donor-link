package com.example.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Donor(
    val id: String = "",
    val name: String = "",
    val bloodGroup: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isAvailable: Boolean = true,
    val phone: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
