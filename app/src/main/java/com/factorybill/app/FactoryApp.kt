package com.factorybill.app

import android.app.Application
import android.util.Log
import com.factorybill.app.BuildConfig
import com.factorybill.app.data.AppDatabase
import com.factorybill.app.data.Repository
import com.factorybill.app.scan.CloudScanConfig
import com.factorybill.app.ui.SettingsStore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class FactoryApp : Application() {
    val db by lazy { AppDatabase.build(this) }
    val repo by lazy { Repository(db) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        if (CloudScanConfig.isConfigured) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
                val firebase = FirebaseApp.initializeApp(this, options, "factorybill-cloud-scan")
                FirebaseAppCheck.getInstance(firebase)
                    .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
            } catch (error: Exception) {
                Log.e("FactoryBill", "Optional cloud bill scanning could not initialize.", error)
            }
        }
    }
}
