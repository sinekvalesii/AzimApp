package com.selcuk.azim

import android.app.Application
import androidx.room.Room
import com.selcuk.azim.data.AzimDb
import com.selcuk.azim.health.HealthRepo

class AzimApp : Application() {
    val db by lazy { Room.databaseBuilder(this, AzimDb::class.java, "azim.db").fallbackToDestructiveMigration(true).build() }
    val health by lazy { HealthRepo(this) }
}
