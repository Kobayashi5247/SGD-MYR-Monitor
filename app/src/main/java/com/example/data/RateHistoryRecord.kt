package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rate_history")
data class RateHistoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val wiseRate: Double,
    val wiseFeeFor1000: Double,
    val wiseNetReceivedFor1000: Double,
    val cimbRate: Double,
    val cimbFeeFor1000: Double,
    val cimbNetReceivedFor1000: Double,
    val source: String = "LIVE"
)
