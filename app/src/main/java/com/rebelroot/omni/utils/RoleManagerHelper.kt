/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 */

package com.rebelroot.omni.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.Q)
object RoleManagerHelper {
    fun isDefaultBrowser(context: Context): Boolean {
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
        return rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
    }

    fun createRequestRoleIntent(context: Context): Intent? {
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return null
        if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
            return rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
        }
        return null
    }

    fun openDefaultBrowserRole(context: Context): Boolean {
        val rm = context.getSystemService(android.app.role.RoleManager::class.java) ?: return false
        if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
            val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER)
            if (context is Activity) {
                context.startActivityForResult(intent, 1001)
                return true
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }
        return false
    }
}
