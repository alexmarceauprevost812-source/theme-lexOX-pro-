package ca.lexos.lanceur

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject

/**
 * Écran de verrouillage LexOS PRO.
 * S'affiche par-dessus l'écran verrouillé de Samsung quand on allume l'écran.
 * Glisser vers le haut → déverrouillage Samsung (empreinte / NIP), donc
 * la sécurité du téléphone reste celle de Samsung.
 */
class VerrouActivity : Activity() {

    private lateinit var web: WebView
    private var lampeAllumee = false

    private val deverrouille = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Empreinte / NIP réussi : on s'en va.
            if (i?.action == Intent.ACTION_USER_PRESENT) fermer()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            settings.javaScriptEnabled = true
            overScrollMode = View.OVER_SCROLL_NEVER
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: android.webkit.WebResourceRequest) =
                    loader.shouldInterceptRequest(r.url)
            }
            addJavascriptInterface(Pont(), "Verrou")
            loadUrl("https://appassets.androidplatform.net/assets/www/verrou.html")
        }
        setContentView(web)
        registerReceiver(deverrouille, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        web.evaluateJavascript("window.verrouRetour && verrouRetour()", null)
    }

    override fun onDestroy() {
        try { unregisterReceiver(deverrouille) } catch (_: Exception) {}
        if (lampeAllumee) torche(false)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* rester sur l'écran de verrouillage */ }

    private fun fermer() {
        if (!isFinishing) { finish(); overridePendingTransition(0, android.R.anim.fade_out) }
    }

    private fun torche(on: Boolean): Boolean = try {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.first {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        cm.setTorchMode(id, on); lampeAllumee = on; on
    } catch (e: Exception) { false }

    inner class Pont {
        @JavascriptInterface
        fun deverrouiller() {
            runOnUiThread {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (km.isKeyguardLocked) {
                    km.requestDismissKeyguard(this@VerrouActivity, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() { fermer() }
                    })
                } else fermer()
            }
        }

        @JavascriptInterface
        fun lampe(on: Boolean): Boolean = torche(on)

        @JavascriptInterface
        fun camera() {
            runOnUiThread {
                try {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }

        @JavascriptInterface
        fun battery(): Int =
            (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        @JavascriptInterface
        fun evenement(): String {
            val pr = getSharedPreferences("lexos", MODE_PRIVATE)
            if (pr.getLong("evt_t", 0L) == 0L) return "null"
            return JSONObject().put("type", pr.getString("evt_type", "sms")).put("nom", pr.getString("evt_nom", ""))
                .put("t", pr.getLong("evt_t", 0L)).toString()
        }

        @JavascriptInterface
        fun calme(): Boolean = getSharedPreferences("lexos", MODE_PRIVATE).getBoolean("calme", false)

        @JavascriptInterface
        fun vibrate() { runOnUiThread { web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) } }
    }
}
