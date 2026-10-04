package com.tbce.calc

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.tbce.calc.vault.KeyVault

/**
 * A launcher face: the icon and name the app shows, and the front screen behind it. READER is the
 * app's own face and DICTIONARY the disguise. Both editions have both; a fresh install starts on
 * the first, READER, which is the alias the manifest enables.
 */
enum class Disguise(val label: String, val alias: String) {
    READER("Reader", "FaceReader"),
    DICTIONARY("Dictionary", "FaceDictionary"),
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

    // Aliases are declared relative to the manifest namespace, which is MainActivity's package
    // (not the application id, which differs per edition). MainActivity keeps its name under R8.
    private val namespace = MainActivity::class.java.name.substringBeforeLast('.')

    private fun component(context: Context, d: Disguise) = ComponentName(context.packageName, "$namespace.${d.alias}")

    /** The faces this edition's manifest declares, in [Disguise] order. */
    fun available(context: Context): List<Disguise> = Disguise.entries.filter { d ->
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getActivityInfo(component(context, d), PackageManager.MATCH_DISABLED_COMPONENTS)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** The launcher name of [d] as this edition shows it (e.g. the play edition's own app name). */
    fun label(context: Context, d: Disguise): String = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(component(context, d), PackageManager.MATCH_DISABLED_COMPONENTS)
        info.loadLabel(context.packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        d.label
    }

    /**
     * The active face, with its alias made the enabled one. A fresh install starts on the first
     * face (READER). An install that was disguised stays disguised: a stored face that no longer
     * exists (Calculator, Notes, Clock and Sudoku were removed in 1.5), or a vault with nothing
     * stored (before 1.3, Calculator was the default and nothing was stored), moves to the
     * Dictionary. Every alias is set explicitly whenever one doesn't match, because the manifest's
     * default changed in 1.6 (FaceReader is now enabled by default, which on its own would add a
     * second icon to a direct install on the Dictionary) and because a face switched away from
     * earlier stays disabled across updates.
     */
    fun current(context: Context): Disguise {
        val faces = available(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY, null)
        faces.firstOrNull { it.name == name }?.let { face ->
            if (!applied(context, face, faces)) set(context, face)
            return face
        }
        val disguised = name != null || KeyVault.existsIn(context.filesDir)
        val face = if (disguised && Disguise.DICTIONARY in faces) Disguise.DICTIONARY else faces.firstOrNull() ?: Disguise.READER
        set(context, face)
        if (name != null) forgetRemovedFaces(context)
        return face
    }

    /** True when [face] is explicitly enabled and every other face explicitly disabled. */
    private fun applied(context: Context, face: Disguise, faces: List<Disguise>): Boolean {
        val pm = context.packageManager
        return faces.all { d ->
            pm.getComponentEnabledSetting(component(context, d)) ==
                if (d == face) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
    }

    /** Deletes what the faces removed in 1.5 left behind (Sudoku's saved game), so it can't hint they were used. */
    private fun forgetRemovedFaces(context: Context) {
        context.deleteSharedPreferences("s")
    }

    fun set(context: Context, disguise: Disguise) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, disguise.name).apply()
        val pm = context.packageManager
        for (d in available(context)) {
            val state = if (d == disguise) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            pm.setComponentEnabledSetting(component(context, d), state, PackageManager.DONT_KILL_APP)
        }
    }
}

/**
 * Runs once after the app is updated, before it is next opened, so the launcher shows the chosen
 * face straight away (see [Disguises.current]) rather than the manifest's default until launch.
 */
class UpdateReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_MY_PACKAGE_REPLACED) Disguises.current(context)
    }
}
