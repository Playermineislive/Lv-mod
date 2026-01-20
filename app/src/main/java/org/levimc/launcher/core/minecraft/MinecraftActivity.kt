package org.levimc.launcher.core.minecraft

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.mojang.minecraftpe.MainActivity
import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager
import org.levimc.launcher.core.versions.GameVersion
import org.levimc.launcher.settings.FeatureSettings
import java.io.File

class MinecraftActivity : MainActivity() {

    private lateinit var gameManager: GamePackageManager
    private var overlayManager: InbuiltOverlayManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            val versionDir = intent.getStringExtra("MC_PATH")
            val versionCode = intent.getStringExtra("MINECRAFT_VERSION") ?: ""
            val versionDirName = intent.getStringExtra("MINECRAFT_VERSION_DIR") ?: ""
            val isInstalled = intent.getBooleanExtra("IS_INSTALLED", false)
            val isIsolated = FeatureSettings.getInstance().isVersionIsolationEnabled()

            val version = if (isIsolated && !versionDir.isNullOrEmpty()) {
                GameVersion(
                    versionDirName,
                    versionCode,
                    versionCode,
                    File(versionDir),
                    isInstalled,
                    MinecraftLauncher.MC_PACKAGE_NAME,
                    ""
                )
            } else if (!versionCode.isNullOrEmpty()) {
                GameVersion(
                    versionDirName,
                    versionCode,
                    versionCode,
                    File(versionDir ?: ""),
                    true,
                    MinecraftLauncher.MC_PACKAGE_NAME,
                    ""
                )
            } else {
                null
            }

            gameManager = GamePackageManager.getInstance(applicationContext, version)

            try {
                System.loadLibrary("preloader")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load preloader: ${e.message}")
            }

            if (!gameManager.loadLibrary("minecraftpe")) {
                throw RuntimeException("Failed to load libminecraftpe.so")
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load game: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        MinecraftActivityState.onCreated(this)
    }
    
    private fun startInbuiltModServices() {
        overlayManager = InbuiltOverlayManager(this)
        overlayManager?.showEnabledOverlays()
    }
    
    private fun stopInbuiltModServices() {
        overlayManager?.hideAllOverlays()
        overlayManager = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        MinecraftActivityState.onResumed()

        if (overlayManager == null) {
            startInbuiltModServices()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        overlayManager?.let { manager ->
            if (manager.handleKeyEvent(event.keyCode, event.action)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        overlayManager?.handleTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_BUTTON_PRESS || 
            event.action == MotionEvent.ACTION_BUTTON_RELEASE) {
            overlayManager?.handleMouseEvent(event)
        }
        
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val vScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            if (vScroll != 0f) {
                overlayManager?.let { manager ->
                    if (manager.handleScrollEvent(vScroll)) {
                        return true
                    }
                }
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onPause() {
        MinecraftActivityState.onPaused()
        super.onPause()
    }

    override fun onDestroy() {
        MinecraftActivityState.onDestroyed()
        stopInbuiltModServices()
        super.onDestroy()

        val intent = Intent(applicationContext, org.levimc.launcher.ui.activities.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)

        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun getAssets(): AssetManager {
        return if (::gameManager.isInitialized) {
            gameManager.getAssets()
        } else {
            super.getAssets()
        }
    }

    override fun getFilesDir(): File {
        val mcPath = intent.getStringExtra("MC_PATH")
        val isVersionIsolationEnabled = FeatureSettings.getInstance().isVersionIsolationEnabled()

        return if (isVersionIsolationEnabled && !mcPath.isNullOrEmpty()) {
            val filesDir = File(mcPath, "games/com.mojang")
            if (!filesDir.exists()) {
                filesDir.mkdirs()
            }
            filesDir
        } else {
            super.getFilesDir()
        }
    }

    override fun getDataDir(): File {
        val mcPath = intent.getStringExtra("MC_PATH")
        val isVersionIsolationEnabled = FeatureSettings.getInstance().isVersionIsolationEnabled()

        return if (isVersionIsolationEnabled && !mcPath.isNullOrEmpty()) {
            val dataDir = File(mcPath)
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }
            dataDir
        } else {
            super.getDataDir()
        }
    }

    override fun getExternalFilesDir(type: String?): File? {
        val mcPath = intent.getStringExtra("MC_PATH")
        val isVersionIsolationEnabled = FeatureSettings.getInstance().isVersionIsolationEnabled()

        return if (isVersionIsolationEnabled && !mcPath.isNullOrEmpty()) {
            val externalDir = if (type != null) {
                File(mcPath, "games/com.mojang/$type")
            } else {
                File(mcPath, "games/com.mojang")
            }
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }
            externalDir
        } else {
            super.getExternalFilesDir(type)
        }
    }

    override fun getDatabasePath(name: String): File {
        val mcPath = intent.getStringExtra("MC_PATH")
        val isVersionIsolationEnabled = FeatureSettings.getInstance().isVersionIsolationEnabled()

        return if (isVersionIsolationEnabled && !mcPath.isNullOrEmpty()) {
            val dbDir = File(mcPath, "databases")
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            File(dbDir, name)
        } else {
            super.getDatabasePath(name)
        }
    }

    override fun getCacheDir(): File {
        val mcPath = intent.getStringExtra("MC_PATH")
        val isVersionIsolationEnabled = FeatureSettings.getInstance().isVersionIsolationEnabled()

        return if (isVersionIsolationEnabled && !mcPath.isNullOrEmpty()) {
            val cacheDir = File(mcPath, "cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            cacheDir
        } else {
            super.getCacheDir()
        }
    }
// ==========================================
    // FIXED RESOLUTION SCALER (ENFORCER)
    // ==========================================

    // Variable to track our enforcer callback
    private var resolutionEnforcer: android.view.SurfaceHolder.Callback? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyResolutionScale()
        }
    }

    private fun applyResolutionScale() {
        val prefs = getSharedPreferences("LauncherPrefs", android.content.Context.MODE_PRIVATE)
        val scale = prefs.getFloat("render_scale", 1.0f)

        // Only run if scale is enabled (less than 1.0)
        if (scale < 1.0f) {
            val decorView = window.decorView as? android.view.ViewGroup
            val surfaceView = findSurfaceView(decorView)

            if (surfaceView != null) {
                surfaceView.holder?.let { holder ->
                    val metrics = android.util.DisplayMetrics()
                    windowManager.defaultDisplay.getRealMetrics(metrics)

                    // 1. Calculate target size
                    val targetWidth = (metrics.widthPixels * scale).toInt()
                    val targetHeight = (metrics.heightPixels * scale).toInt()

                    // 2. Apply it immediately
                    holder.setFixedSize(targetWidth, targetHeight)

                    // 3. THE FIX: Add a callback to STOP Minecraft from resetting it
                    if (resolutionEnforcer == null) {
                        resolutionEnforcer = object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(h: android.view.SurfaceHolder) {}
                            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}

                            override fun surfaceChanged(h: android.view.SurfaceHolder, format: Int, w: Int, height: Int) {
                                // If game resets size, FORCE IT BACK
                                if (w != targetWidth || height != targetHeight) {
                                    runOnUiThread {
                                        holder.setFixedSize(targetWidth, targetHeight)
                                    }
                                }
                            }
                        }
                        holder.addCallback(resolutionEnforcer)
                    }

                    // Debug: Show a Toast so you KNOW it worked
                    android.widget.Toast.makeText(this, "Res: ${targetWidth}x${targetHeight}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Helper to find the Game View
    private fun findSurfaceView(group: android.view.ViewGroup?): android.view.SurfaceView? {
        if (group == null) return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is android.view.SurfaceView) {
                return child
            }
            if (child is android.view.ViewGroup) {
                val result = findSurfaceView(child)
                if (result != null) return result
            }
        }
        return null
    }
    companion object {
        private const val TAG = "MinecraftActivity"
    }
}