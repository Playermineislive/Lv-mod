package org.levimc.launcher.core.minecraft

import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
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
    //  RESOLUTION ENFORCER VARIABLE
    //  (Must be declared here to persist across game engine resets)
    // =============================================================
    private var resolutionEnforcer: SurfaceHolder.Callback? = null

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

    // =============================================================
    //  HYBRID RESOLUTION SCALER (Fixed Size + Layout + Visual)
    // =============================================================

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Delay is critical: Let the Minecraft Engine finish its startup
            // before we hijack the surface. Prevents crashes/black screens.
            window.decorView.postDelayed({ applyResolutionScale() }, 500)
        }
    }

    private fun applyResolutionScale() {
        try {
            val prefs = getSharedPreferences("LauncherPrefs", android.content.Context.MODE_PRIVATE)
            val scale = prefs.getFloat("render_scale", 1.0f)

            // If user wants Native (1.0), do nothing and let the game run normally.
            if (scale >= 1.0f) return

            val decorView = window.decorView as? ViewGroup
            val surfaceView = findSurfaceView(decorView)

            if (surfaceView != null) {
                runOnUiThread {
                    val metrics = DisplayMetrics()
                    windowManager.defaultDisplay.getRealMetrics(metrics)

                    // 1. Calculate Target Low-Res Size
                    val targetWidth = (metrics.widthPixels * scale).toInt()
                    val targetHeight = (metrics.heightPixels * scale).toInt()

                    // 2. Calculate Stretch Factor (To visually fill screen)
                    val stretchFactor = 1.0f / scale

                    // --- METHOD A: Hardware Buffer (Primary Efficiency) ---
                    // Tells the GPU to render to a smaller buffer.
                    surfaceView.holder.setFixedSize(targetWidth, targetHeight)

                    // --- METHOD B: Physical Layout (Engine Restriction) ---
                    // Physically shrink the view so the engine CANNOT render 1080p.
                    val params = surfaceView.layoutParams
                    if (params.width != targetWidth || params.height != targetHeight) {
                        params.width = targetWidth
                        params.height = targetHeight
                        surfaceView.layoutParams = params
                    }

                    // --- METHOD C: Visual Stretching (Full Screen Restore) ---
                    // Zoom the tiny view back up to cover the screen.
                    surfaceView.pivotX = 0f
                    surfaceView.pivotY = 0f
                    surfaceView.scaleX = stretchFactor
                    surfaceView.scaleY = stretchFactor

                    // --- METHOD D: The Enforcer (Anti-Reset) ---
                    // Minecraft WILL try to reset resolution. We watch for it and stop it.
                    if (resolutionEnforcer == null) {
                        resolutionEnforcer = object : SurfaceHolder.Callback {
                            override fun surfaceCreated(h: SurfaceHolder) {}
                            override fun surfaceDestroyed(h: SurfaceHolder) {}

                            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hInt: Int) {
                                // If the size changes to something other than our target...
                                if (w != targetWidth || hInt != targetHeight) {
                                    // ...Force it back immediately.
                                    runOnUiThread {
                                        surfaceView.holder.setFixedSize(targetWidth, targetHeight)
                                        surfaceView.layoutParams.width = targetWidth
                                        surfaceView.layoutParams.height = targetHeight
                                        surfaceView.requestLayout()
                                    }
                                }
                            }
                        }
                        surfaceView.holder.addCallback(resolutionEnforcer)
                    }

                    // User Feedback
                    Toast.makeText(this, "🚀 Resolution: ${targetWidth}x${targetHeight}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("LeviLauncher", "Resolution Scaler Error: ${e.message}")
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