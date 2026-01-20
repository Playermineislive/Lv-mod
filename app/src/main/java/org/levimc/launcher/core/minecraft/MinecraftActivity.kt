package org.levimc.launcher.core.minecraft

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.Toast
import com.mojang.minecraftpe.MainActivity
import org.levimc.launcher.core.mods.inbuilt.overlay.InbuiltOverlayManager
import org.levimc.launcher.core.versions.GameVersion
import org.levimc.launcher.settings.FeatureSettings
import java.io.File

class MinecraftActivity : MainActivity() {

    private lateinit var gameManager: GamePackageManager
    private var overlayManager: InbuiltOverlayManager? = null

    // =============================================================
    //  PERSISTENT RESOLUTION MANAGER
    // =============================================================
    private val resolutionHandler = Handler(Looper.getMainLooper())
    private var isWatcherRunning = false

    // This runnable loops forever to ensure resolution stays low
    private val resolutionWatcher = object : Runnable {
        override fun run() {
            if (isWatcherRunning) {
                applyResolutionScale()
                // Check again in 2 seconds (2000ms)
                resolutionHandler.postDelayed(this, 2000)
            }
        }
    }

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

        // START WATCHING RESOLUTION
        if (!isWatcherRunning) {
            isWatcherRunning = true
            resolutionHandler.post(resolutionWatcher)
        }
    }

    override fun onPause() {
        MinecraftActivityState.onPaused()
        super.onPause()
        
        // STOP WATCHING to save battery when app is minimized
        isWatcherRunning = false
        resolutionHandler.removeCallbacks(resolutionWatcher)
    }

    override fun onDestroy() {
        MinecraftActivityState.onDestroyed()
        stopInbuiltModServices()
        
        // Kill the watcher
        isWatcherRunning = false
        resolutionHandler.removeCallbacksAndMessages(null)

        super.onDestroy()

        val intent = Intent(applicationContext, org.levimc.launcher.ui.activities.MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)

        finishAndRemoveTask()
        android.os.Process.killProcess(android.os.Process.myPid())
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

    // =============================================================
    //  THE RESOLUTION LOGIC
    // =============================================================
    
    private fun applyResolutionScale() {
        try {
            val prefs = getSharedPreferences("LauncherPrefs", android.content.Context.MODE_PRIVATE)
            val scale = prefs.getFloat("render_scale", 1.0f)

            // If Native (1.0), stop here.
            if (scale >= 1.0f) return

            // 1. Find the View (FRESH every time, in case the game recreated it)
            val decorView = window.decorView as? ViewGroup
            val surfaceView = findSurfaceView(decorView)

            if (surfaceView != null) {
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)

                val targetWidth = (metrics.widthPixels * scale).toInt()
                val targetHeight = (metrics.heightPixels * scale).toInt()

                // 2. CHECK if it's already correct to avoid spamming the system
                // If the view is already shrunk, we don't need to do anything.
                if (surfaceView.width == targetWidth && surfaceView.height == targetHeight) {
                    return
                }

                // 3. APPLY THE FIX
                runOnUiThread {
                    Log.d("LeviResolution", "Enforcing Resolution: ${targetWidth}x${targetHeight}")
                    
                    // Force the buffer size (GPU optimization)
                    surfaceView.holder.setFixedSize(targetWidth, targetHeight)
                    
                    // Force the physical layout size (Engine restriction)
                    val params = surfaceView.layoutParams
                    params.width = targetWidth
                    params.height = targetHeight
                    surfaceView.layoutParams = params
                    
                    // Stretch it back to full screen visually
                    val stretchFactor = 1.0f / scale
                    surfaceView.pivotX = 0f
                    surfaceView.pivotY = 0f
                    surfaceView.scaleX = stretchFactor
                    surfaceView.scaleY = stretchFactor
                    
                    // Show a toast only if we actually changed something
                    // Toast.makeText(this, "Refreshed Res", Toast.LENGTH_SHORT).show() 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resolution Watcher Error: ${e.message}")
        }
    }

    private fun findSurfaceView(group: ViewGroup?): SurfaceView? {
        if (group == null) return null
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is SurfaceView) return child
            if (child is ViewGroup) {
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