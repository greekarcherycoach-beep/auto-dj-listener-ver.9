package com.djape1.player

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Σκοτεινό θέμα από προεπιλογή, όπως στον player του browser
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.dark(this)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
