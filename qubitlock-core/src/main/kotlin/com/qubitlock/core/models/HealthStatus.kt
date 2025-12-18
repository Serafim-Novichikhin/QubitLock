package com.qubitlock.core.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String,
    val timestamp: String,
    val version: String = "1.0.0"
)
