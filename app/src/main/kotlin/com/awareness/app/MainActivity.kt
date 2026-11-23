package com.awareness.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.awareness.feature.settings.SettingsActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
