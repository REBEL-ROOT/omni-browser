package com.rebelroot.omni.tools.locker

import android.content.Context

class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("omni_locker_prefs", Context.MODE_PRIVATE)

    fun isPinSet(): Boolean {
        return prefs.getString("in_app_pin", null) != null
    }

    fun setPin(pin: String) {
        prefs.edit().putString("in_app_pin", pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        return prefs.getString("in_app_pin", null) == pin
    }
}
