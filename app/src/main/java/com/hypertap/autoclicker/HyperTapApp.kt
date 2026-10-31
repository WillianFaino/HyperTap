package com.hypertap.autoclicker

import android.app.Application
import com.hypertap.autoclicker.data.local.HyperTapDatabase
import com.hypertap.autoclicker.data.repository.MacroRepository

class HyperTapApp : Application() {
    lateinit var repository: MacroRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = HyperTapDatabase.getDatabase(this)
        repository = MacroRepository(database.macroDao())
    }
}

