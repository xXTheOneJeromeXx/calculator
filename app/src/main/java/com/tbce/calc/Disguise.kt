package com.tbce.calc

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** A launcher face: the icon and name the app shows, and the front screen behind it. */
enum class Disguise(val label: String, val alias: String) {
    CALCULATOR("Calculator", "FaceCalculator"),
    NOTES("Notes", "FaceNotes"),
}

/**
 * Switches which launcher face is active. Each face is an activity-alias in the manifest; exactly
 * one is enabled at a time, so the home screen and app list show only that icon and name. The
 * choice is kept in a small preference (the enabled alias already reveals it to the system, so
 * this stores nothing the OS doesn't). It is read before unlock to pick the front screen.
 */
object Disguises {
    private const val PREFS = "d"
    private const val KEY = "f"

    fun current(context: Context): Disguise {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return Disguise.entries.firstOrNull { it.name == name } ?: Disguise.CALCULATOR
    }

    fun set(context: Context, disguise: Disguise) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, disguise.name).apply()
        val pm = context.packageManager
        val pkg = context.packageName
        for (d in Disguise.entries) {
            val state = if (d == disguise) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$pkg.${d.alias}"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
