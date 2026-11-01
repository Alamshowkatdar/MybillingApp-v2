package com.factorybill.app

import android.app.Application
import com.factorybill.app.data.AppDatabase
import com.factorybill.app.data.Repository
import com.factorybill.app.ui.SettingsStore

class FactoryApp : Application() {
    val db by lazy { AppDatabase.build(this) }
    val repo by lazy { Repository(db) }
    val settings by lazy { SettingsStore(this) }
}
