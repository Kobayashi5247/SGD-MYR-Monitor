package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RateDao {
    @Query("SELECT * FROM rate_alerts ORDER BY id ASC")
    fun getAllAlerts(): Flow<List<RateAlert>>

    @Query("SELECT * FROM rate_alerts ORDER BY id ASC")
    suspend fun getAllAlertsList(): List<RateAlert>

    @Query("SELECT * FROM rate_alerts WHERE id = :id")
    suspend fun getAlertById(id: Int): RateAlert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: RateAlert)

    @Query("SELECT * FROM rate_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<RateHistoryRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(record: RateHistoryRecord)

    @Query("DELETE FROM rate_history")
    suspend fun clearHistory()
}
