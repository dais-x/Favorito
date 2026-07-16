package com.favorito

import android.app.Application
import com.favorito.data.FavoritoDatabase

class FavoritoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        FavoritoDatabase.get(this)
    }
}
