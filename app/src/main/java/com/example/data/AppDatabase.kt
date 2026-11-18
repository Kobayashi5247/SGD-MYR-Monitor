package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [RateAlert::class, RateHistoryRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rateDao(): RateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sgd_myr_rate_db"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default 5 alerts
                        INSTANCE?.let { database ->
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = database.rateDao()
                                val defaultAlerts = listOf(
                                    RateAlert(id = 1, targetRate = 3.2800, condition = RateAlert.CONDITION_GREATER_EQUAL, provider = RateAlert.PROVIDER_ANY, isEnabled = true, label = "提醒 1 (≥ 3.2800)"),
                                    RateAlert(id = 2, targetRate = 3.3000, condition = RateAlert.CONDITION_GREATER_EQUAL, provider = RateAlert.PROVIDER_WISE, isEnabled = true, label = "提醒 2 (Wise ≥ 3.3000)"),
                                    RateAlert(id = 3, targetRate = 3.2700, condition = RateAlert.CONDITION_GREATER_EQUAL, provider = RateAlert.PROVIDER_CIMB_SG, isEnabled = true, label = "提醒 3 (CIMB ≥ 3.2700)"),
                                    RateAlert(id = 4, targetRate = 3.2500, condition = RateAlert.CONDITION_LESS_EQUAL, provider = RateAlert.PROVIDER_ANY, isEnabled = false, label = "提醒 4 (≤ 3.2500)"),
                                    RateAlert(id = 5, targetRate = 3.3200, condition = RateAlert.CONDITION_GREATER_EQUAL, provider = RateAlert.PROVIDER_ANY, isEnabled = false, label = "提醒 5 (≥ 3.3200)")
                                )
                                defaultAlerts.forEach { dao.insertAlert(it) }
                            }
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
