package com.selcuk.azim.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Görev tanımı. target, zorluk algoritması tarafından günden güne güncellenir. */
@Entity(tableName = "quests")
data class Quest(
    @PrimaryKey val id: String,
    val title: String,
    val unit: String,
    val target: Double,
    val step: Double,
    val min: Double,
    val max: Double,
    val xp: Int,
    val auto: Boolean = false // true: ilerleme Health Connect'ten gelir (adım)
)

/** Bir görevin belirli bir gündeki hedefi ve ilerlemesi. */
@Entity(tableName = "logs", primaryKeys = ["date", "questId"])
data class DayLog(val date: String, val questId: String, val target: Double, val progress: Double = 0.0)

val DayLog.done: Boolean get() = progress >= target

/** Health Connect'ten okunan günlük özet. */
@Entity(tableName = "health")
data class HealthDay(@PrimaryKey val date: String, val steps: Long, val avgHr: Long?)

@Dao
interface AzimDao {
    @Query("SELECT * FROM quests") fun quests(): Flow<List<Quest>>
    @Query("SELECT * FROM quests") suspend fun questsOnce(): List<Quest>
    @Upsert suspend fun upsertQuests(q: List<Quest>)

    @Query("SELECT * FROM logs ORDER BY date") fun allLogs(): Flow<List<DayLog>>
    @Query("SELECT * FROM logs ORDER BY date") suspend fun allLogsOnce(): List<DayLog>
    @Query("SELECT * FROM logs WHERE date = :date") suspend fun logsOnce(date: String): List<DayLog>
    @Upsert suspend fun upsertLog(l: DayLog)
    @Upsert suspend fun upsertLogs(l: List<DayLog>)

    @Query("SELECT * FROM health ORDER BY date") fun health(): Flow<List<HealthDay>>
    @Query("SELECT * FROM health ORDER BY date") suspend fun healthOnce(): List<HealthDay>
    @Upsert suspend fun upsertHealth(h: List<HealthDay>)
}

@Database(entities = [Quest::class, DayLog::class, HealthDay::class], version = 1, exportSchema = false)
abstract class AzimDb : RoomDatabase() {
    abstract fun dao(): AzimDao
}
