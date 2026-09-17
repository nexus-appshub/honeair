package com.example.security

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.WindowManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

object SecurityGuard {

    private const val TAG = "SecurityGuard"

    /**
     * Checks if the Android device is rooted or modified with Magisk/Superuser.
     */
    fun isDeviceRooted(context: Context): Boolean {
        return checkBuildTags() || checkRootPaths() || checkSuCommand()
    }

    /**
     * Checks if a network proxy (e.g. Charles, Fiddler, HTTPCanary) or VPN inspection is active.
     */
    fun isProxyOrVpnActive(context: Context): Boolean {
        return checkSystemProxy(context) || checkVpnActive(context)
    }

    /**
     * Detects if the app is running under an insecure or modified Android Emulator.
     */
    fun isInsecureEnvironment(context: Context): Boolean {
        val isEmulator = (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.HARDWARE.contains("vbox86")
                || Build.HARDWARE.contains("nox"))

        val hasQemuDriver = File("/dev/socket/qemud").exists() || File("/dev/qemu_pipe").exists()
        return isEmulator || hasQemuDriver
    }

    /**
     * Allows screenshots and screen recording across the app by explicitly clearing FLAG_SECURE.
     */
    fun applyScreenProtection(activity: Activity) {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing FLAG_SECURE", e)
        }
    }

    fun allowScreenCapture(activity: Activity) {
        try {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing FLAG_SECURE", e)
        }
    }

    /**
     * XOR dynamic string decryptor for anti-scraping URL protection.
     * Keeps URLs obfuscated in compiled byte code so decompiler tools cannot extract plain links.
     */
    fun decryptSecureUrl(encryptedHex: String, key: String = "XUBI_SECURE_2026"): String {
        return try {
            val bytes = encryptedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val keyBytes = key.toByteArray()
            val result = ByteArray(bytes.size)
            for (i in bytes.indices) {
                result[i] = (bytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(result, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting stream URL", e)
            ""
        }
    }

    // --- Private Security Sub-checks ---

    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootPaths(): Boolean {
        val knownRootPaths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/xbin/daemonsu",
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/app/Magisk.apk",
            "/system/priv-app/Magisk.apk",
            "/data/adb/magisk"
        )
        for (path in knownRootPaths) {
            if (File(path).exists()) {
                return true
            }
        }
        return false
    }

    private fun checkSuCommand(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (t: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }

    private fun checkSystemProxy(context: Context): Boolean {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        if (!proxyHost.isNullOrBlank() && proxyPort != null && proxyPort != "-1") {
            return true
        }
        val httpsProxyHost = System.getProperty("https.proxyHost")
        val httpsProxyPort = System.getProperty("https.proxyPort")
        return !httpsProxyHost.isNullOrBlank() && httpsProxyPort != null && httpsProxyPort != "-1"
    }

    private fun checkVpnActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }
}
