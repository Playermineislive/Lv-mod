package org.levimc.launcher.core.minecraft
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.util.DisplayMetrics
import android.util.Log
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
// Variable to hold the enforcer callback so we can remove it later if needed
    private var resolutionEnforcer: android.view.SurfaceHolder.Callback? = null

    private fun applyResolutionScale() {
        val prefs = getSharedPreferences("LauncherPrefs", android.content.Context.MODE_PRIVATE)
        val scale = prefs.getFloat("render_scale", 1.0f)

        // Only run if the user actually requested scaling (less than 100%)
        if (scale < 1.0f) {
            val decorView = window.decorView as? android.view.ViewGroup
            val surfaceView = findSurfaceView(decorView)

            if (surfaceView != null) {
                // We run on the UI thread to ensure visual changes apply instantly
                runOnUiThread {
                    val metrics = android.util.DisplayMetrics()
                    windowManager.defaultDisplay.getRealMetrics(metrics)

                    // 1. Calculate the SMALL resolution (The one we want for performance)
                    // Example: 1080p * 0.5 = 540p
                    val targetWidth = (metrics.widthPixels * scale).toInt()
                    val targetHeight = (metrics.heightPixels * scale).toInt()
                    
                    // 2. Calculate the STRETCH factor to make it look full-screen
                    // Example: 1.0 / 0.5 = 2.0x zoom
                    val stretchFactor = 1.0f / scale

                    // 3. FORCE the View to be physically small
                    // This forces the GPU to only render the small number of pixels.
                    val params = surfaceView.layoutParams
                    params.width = targetWidth
                    params.height = targetHeight
                    surfaceView.layoutParams = params

                    // 4. STRETCH it back to fill the screen
                    // We set the pivot to (0,0) so it scales out from the top-left corner
                    surfaceView.pivotX = 0f
                    surfaceView.pivotY = 0f
                    surfaceView.scaleX = stretchFactor
                    surfaceView.scaleY = stretchFactor

                    // 5. THE ENFORCER: Stop Minecraft from fixing it
                    // The game engine will try to reset the size. We watch for that and stop it.
                    if (resolutionEnforcer == null) {
                        resolutionEnforcer = object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(h: android.view.SurfaceHolder) {}
                            override fun surfaceDestroyed(h: android.view.SurfaceHolder) {}

                            override fun surfaceChanged(h: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                                // If the game changed the size to something other than our target...
                                if (width != targetWidth || height != targetHeight) {
                                    // ...we force it back immediately.
                                    runOnUiThread {
                                        if (surfaceView.layoutParams.width != targetWidth) {
                                            surfaceView.layoutParams.width = targetWidth
                                            surfaceView.layoutParams.height = targetHeight
                                            surfaceView.requestLayout()
                                        }
                                    }
                                }
                            }
                        }
                        surfaceView.holder.addCallback(resolutionEnforcer)
                    }

                    android.widget.Toast.makeText(this, "⚡ FPS Boost Active: ${targetWidth}x${targetHeight}", android.widget.Toast.LENGTH_LONG).show()
                 }
            }
        }
    }
    companion object {
        private const val TAG = "MinecraftActivity"
    }
}