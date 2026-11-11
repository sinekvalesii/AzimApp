package com.selcuk.azim.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Uygulama durumu (hesaplar, ilerleme, ayarlar) anahtar-değer olarak saklanır. */
@Entity(tableName = "kv")
data class Kv(@PrimaryKey val k: String, val v: String)

/** Health Connect'ten okunan günlük özet. */
@Entity(tableName = "health")
data class HealthDay(@PrimaryKey val date: String, val steps: Long, val avgHr: Long?)

/** Sorgular JavaScript köprüsünün arka plan iş parçacığından senkron çağrılır (ana iş parçacığı değil). */
@Dao
interface AzimDao {
    @Query("SELECT v FROM kv WHERE k = :k") fun get(k: String): String?
    @Upsert fun put(kv: Kv)
    @Upsert fun putAll(kv: List<Kv>)
    @Query("DELETE FROM kv WHERE k = :k") fun remove(k: String)
    @Query("SELECT COUNT(*) FROM kv") fun count(): Int
    @Query("SELECT * FROM health ORDER BY date") fun health(): List<HealthDay>
    @Upsert fun upsertHealth(h: List<HealthDay>)
}

@Database(entities = [Kv::class, HealthDay::class], version = 2, exportSchema = false)
abstract class AzimDb : RoomDatabase() {
    abstract fun dao(): AzimDao
}
