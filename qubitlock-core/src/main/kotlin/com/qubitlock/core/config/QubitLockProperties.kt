package com.qubitlock.core.config

import kotlinx.serialization.Serializable

@Serializable
data class QubitLockProperties(
    val vault: VaultConfig,
    val mongodb: MongoDBConfig,
    val features: Features
) {
    @Serializable
    data class VaultConfig(
        val url: String,
        val token: String,
        val path: String = "transit"
    )

    @Serializable
    data class MongoDBConfig(
        val connectionString: String,
        val database: String
    )

    @Serializable
    data class Features(
        val compression: Boolean = true,
        val merkleTree: Boolean = true
    )
}