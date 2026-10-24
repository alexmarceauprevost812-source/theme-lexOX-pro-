package ca.lexos.lanceur

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.graphics.drawable.toBitmap
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * LexOS PRO — lanceur Android.
 * Toute l'interface (accueil, tiroir, animations terminal) est en HTML/CSS
 * dans assets/www ; ce fichier ne fait que le pont avec Android :
 * lister les applis, les ouvrir, donner la batterie, ouvrir les réglages.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private val iconCache = HashMap<String, String>()
    private var choixFichier: ValueCallback<Array<Uri>>? = null
    private val CODE_PHOTO = 42

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            iconCache.clear()
            web.post { web.evaluateJavascript("window.lexosRefresh && lexosRefresh()", null) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: android.webkit.WebResourceRequest) =
                    loader.shouldInterceptRequest(r.url)
            }
            // Permet à l'utilisateur de choisir une photo (widget, fond du lanceur).
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
                ): Boolean {
                    choixFichier?.onReceiveValue(null)
                    choixFichier = cb
                    return try {
                        val i = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
                        startActivityForResult(Intent.createChooser(i, "Choisir une photo"), CODE_PHOTO)
                        true
                    } catch (e: Exception) {
                        choixFichier = null
                        false
                    }
                }
            }
            addJavascriptInterface(Bridge(), "LexOS")
            loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
        }
        setContentView(web)

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CODE_PHOTO) {
            choixFichier?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            choixFichier = null
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    // Bouton « accueil » pendant qu'on est déjà sur le lanceur : revenir à l'accueil.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        web.evaluateJavascript("window.lexosHome && lexosHome()", null)
    }

    override fun onResume() {
        super.onResume()
        web.evaluateJavascript("window.lexosResume && lexosResume()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un lanceur ne se ferme pas : on referme le tiroir s'il est ouvert.
        web.evaluateJavascript("window.lexosBack && lexosBack()", null)
    }

    inner class Bridge {

        @JavascriptInterface
        fun getApps(): String {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(main, 0)
            val out = JSONArray()
            for (ri in list) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName) continue
                out.put(JSONObject().apply {
                    put("label", ri.loadLabel(pm).toString())
                    put("pkg", pkg)
                    put("cls", ri.activityInfo.name)
                })
            }
            return out.toString()
        }

        @JavascriptInterface
        fun getIcon(pkg: String): String {
            iconCache[pkg]?.let { return it }
            return try {
                val d = packageManager.getApplicationIcon(pkg)
                val bmp: Bitmap = d.toBitmap(144, 144, Bitmap.Config.ARGB_8888)
                val bos = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                val url = "data:image/png;base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
                iconCache[pkg] = url
                url
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun launch(pkg: String, cls: String) {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    startActivity(i)
                } catch (e: Exception) {
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                }
            }
        }

        @JavascriptInterface
        fun appInfo(pkg: String) {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface
        fun battery(): Int {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        }

        @JavascriptInterface
        fun device(): String = android.os.Build.MODEL ?: "Android"

        @JavascriptInterface
        fun openSettings() {
            runOnUiThread { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        @JavascriptInterface
        fun openWallpaper() {
            runOnUiThread {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fond d'écran")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface
        fun expandNotifications() {
            runOnUiThread {
                try {
                    @SuppressLint("WrongConstant")
                    val sb = getSystemService("statusbar")
                    val c = Class.forName("android.app.StatusBarManager")
                    c.getMethod("expandNotificationsPanel").invoke(sb)
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun search(q: String) {
            runOnUiThread {
                try {
                    startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra("query", q)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }

        @JavascriptInterface
        fun vibrate() {
            runOnUiThread { web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }
}
