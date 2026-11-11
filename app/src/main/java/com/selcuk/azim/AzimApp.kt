package com.selcuk.azim

import android.app.Application
import androidx.room.Room
import com.selcuk.azim.auth.AuthRepo
import com.selcuk.azim.data.AzimDb
import com.selcuk.azim.data.Repo
import com.selcuk.azim.health.HealthRepo

/** Basit servis konumlayıcı: veritabanı ve depolar tek yerde oluşturulur. */
class AzimApp : Application() {
    val db by lazy { Room.databaseBuilder(this, AzimDb::class.java, "azim.db").build() }
    val repo by lazy { Repo(db) }
    val health by lazy { HealthRepo(this) }
    val auth by lazy { AuthRepo(this) }
}
