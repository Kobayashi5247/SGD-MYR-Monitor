package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rate_alerts")
data class RateAlert(
    @PrimaryKey val id: Int, // 1 to 5
    val targetRate: Double = 3.2500,
    val condition: String = CONDITION_GREATER_EQUAL, // "GREATER_EQUAL" or "LESS_EQUAL"
    val provider: String = PROVIDER_ANY, // "WISE", "CIMB_SG", "ANY"
    val isEnabled: Boolean = true,
    val label: String = "Alert #$id",
    val lastTriggeredAt: Long = 0L
) {
    companion object {
        const val CONDITION_GREATER_EQUAL = "GREATER_EQUAL"
        const val CONDITION_LESS_EQUAL = "LESS_EQUAL"

        const val PROVIDER_WISE = "WISE"
        const val PROVIDER_CIMB_SG = "CIMB_SG"
        const val PROVIDER_ANY = "ANY"
    }
}
