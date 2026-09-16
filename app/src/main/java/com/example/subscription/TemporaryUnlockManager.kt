package com.example.subscription

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object TemporaryUnlockManager {
    private const val TAG = "TemporaryUnlockManager"
    private const val PREFS_NAME = "temp_item_unlocks"
    private const val KEY_PREFIX = "unlock_"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getPrefs(context: Context? = null): SharedPreferences? {
        if (prefs == null && context != null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return prefs
    }

    /**
     * Checks if a specific item (channel, movie, anime, or episode) is currently temporarily unlocked.
     */
    fun isItemTemporarilyUnlocked(itemId: String, context: Context? = null, durationMinutes: Int = 30): Boolean {
        if (itemId.isBlank()) return false
        val sp = getPrefs(context) ?: return false
        val key = "${KEY_PREFIX}${itemId.trim()}"
        val unlockedAt = sp.getLong(key, 0L)
        if (unlockedAt <= 0L) return false

        val durationMs = durationMinutes * 60 * 1000L
        val elapsed = System.currentTimeMillis() - unlockedAt
        val isStillValid = elapsed in 0 until durationMs

        if (!isStillValid) {
            sp.edit().remove(key).apply()
            Log.d(TAG, "Temporary unlock expired for item: $itemId")
            return false
        }
        return true
    }

    /**
     * Grants temporary unlock access for a specific item ID.
     */
    fun grantTemporaryUnlock(itemId: String, context: Context? = null, durationMinutes: Int = 30) {
        if (itemId.isBlank()) return
        val sp = getPrefs(context)
        if (sp != null) {
            val key = "${KEY_PREFIX}${itemId.trim()}"
            sp.edit().putLong(key, System.currentTimeMillis()).apply()
            Log.d(TAG, "Granted temporary unlock ($durationMinutes mins) for item: $itemId")
        } else {
            Log.e(TAG, "SharedPreferences null in grantTemporaryUnlock for item: $itemId")
        }
    }

    /**
     * Gets remaining unlocked time in milliseconds for a specific item, or 0 if expired/not unlocked.
     */
    fun getRemainingUnlockTimeMs(itemId: String, context: Context? = null, durationMinutes: Int = 30): Long {
        if (itemId.isBlank()) return 0L
        val sp = getPrefs(context) ?: return 0L
        val key = "${KEY_PREFIX}${itemId.trim()}"
        val unlockedAt = sp.getLong(key, 0L)
        if (unlockedAt <= 0L) return 0L

        val durationMs = durationMinutes * 60 * 1000L
        val elapsed = System.currentTimeMillis() - unlockedAt
        val remaining = durationMs - elapsed
        return if (remaining > 0) remaining else 0L
    }
}
