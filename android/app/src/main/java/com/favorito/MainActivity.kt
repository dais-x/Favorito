package com.favorito

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(FavoritoPlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
