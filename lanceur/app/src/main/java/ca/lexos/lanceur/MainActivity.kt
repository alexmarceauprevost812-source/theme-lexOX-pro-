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
    private val CODE_APPEL = 7
    private var numeroEnAttente: String? = null

    private val deverrouillage = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            web.post { web.evaluateJavascript("window.lexosDeverrouille && lexosDeverrouille()", null) }
        }
    }

    private fun js(code: String) {
        web.post { web.evaluateJavascript(code, null) }
    }

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

        registerReceiver(deverrouillage, IntentFilter(Intent.ACTION_USER_PRESENT))
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

    private fun lancerAppel(num: String, direct: Boolean) {
        val uri = Uri.parse("tel:" + Uri.encode(num))
        try {
            startActivity(Intent(if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODE_APPEL) {
            val num = numeroEnAttente ?: return
            numeroEnAttente = null
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            lancerAppel(num, ok)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(packageReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(deverrouillage) } catch (_: Exception) {}
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
        // SMS / appel reçu pendant qu'on était ailleurs : l'animer à l'accueil
        val pr = getSharedPreferences("lexos", MODE_PRIVATE)
        val t = pr.getLong("evt_t", 0L)
        if (t > pr.getLong("evt_vu", 0L) && System.currentTimeMillis() - t < 10 * 60 * 1000) {
            pr.edit().putLong("evt_vu", t).apply()
            val o = JSONObject().put("type", pr.getString("evt_type", "sms")).put("nom", pr.getString("evt_nom", ""))
                .put("pkg", pr.getString("evt_pkg", ""))
            web.evaluateJavascript("window.lexosEvenement && lexosEvenement(" + o.toString() + ")", null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un lanceur ne se ferme pas : on referme le tiroir s'il est ouvert.
        web.evaluateJavascript("window.lexosBack && lexosBack()", null)
    }

    private fun ipWifi(): String? = try {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }?.hostAddress
    } catch (_: Exception) { null }

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

        /** État des autorisations : notifications, bannières, clavier. */
        @JavascriptInterface
        fun etat(): String {
            val notifs = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(this@MainActivity).contains(packageName)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val clavier = imm.enabledInputMethodList.any { it.packageName == packageName }
            return JSONObject().put("notifs", notifs).put("bannieres", Settings.canDrawOverlays(this@MainActivity))
                .put("clavier", clavier).put("verrou", LexOSApp.verrouActif(this@MainActivity)).toString()
        }

        @JavascriptInterface
        fun activerNotifs() {
            runOnUiThread { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        @JavascriptInterface
        fun activerBannieres() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface
        fun activerClavier() {
            runOnUiThread { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        @JavascriptInterface
        fun choisirClavier() {
            runOnUiThread {
                (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
            }
        }

        /** Composeur LexOS : appeler un numéro (demande la permission la 1re fois). */
        @JavascriptInterface
        fun appeler(num: String) {
            runOnUiThread {
                if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    lancerAppel(num, true)
                } else {
                    numeroEnAttente = num
                    requestPermissions(arrayOf(android.Manifest.permission.CALL_PHONE), CODE_APPEL)
                }
            }
        }

        /** Écran de verrouillage LexOS : activer / désactiver / essayer. */
        @JavascriptInterface
        fun setVerrou(on: Boolean) {
            getSharedPreferences("lexos", MODE_PRIVATE).edit().putBoolean("verrou", on).apply()
        }

        @JavascriptInterface
        fun essaiVerrou() {
            runOnUiThread {
                startActivity(Intent(this@MainActivity, VerrouActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        @JavascriptInterface
        fun setCalme(on: Boolean) {
            getSharedPreferences("lexos", MODE_PRIVATE).edit().putBoolean("calme", on).apply()
        }

        /** Couleur principale choisie : partagée avec le clavier et les bannières. */
        @JavascriptInterface
        fun setAccent(acc: String, bouton: String) {
            try {
                getSharedPreferences("lexos", MODE_PRIVATE).edit()
                    .putInt("accent", android.graphics.Color.parseColor(acc))
                    .putInt("bouton", android.graphics.Color.parseColor(bouton)).apply()
            } catch (_: Exception) {}
        }

        /** Adresse wifi de ce téléphone (pour partager son IA). */
        @JavascriptInterface
        fun monIP(): String = ipWifi() ?: ""

        @JavascriptInterface
        fun copier(texte: String) {
            runOnUiThread {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("LexOS", texte))
            }
        }

        /** Cherche toutes les IA locales (Ollama, port 11434) : ce téléphone + tout le réseau wifi. */
        @JavascriptInterface
        fun iaScanner() {
            Thread {
                val cibles = mutableListOf("127.0.0.1")
                val ip = ipWifi()
                if (ip != null) {
                    val base = ip.substringBeforeLast('.')
                    for (n in 1..254) { val h = "$base.$n"; if (h != ip) cibles.add(h) }
                }
                val pool = java.util.concurrent.Executors.newFixedThreadPool(48)
                for (h in cibles) pool.execute {
                    try {
                        java.net.Socket().use { it.connect(java.net.InetSocketAddress(h, 11434), 450) }
                        val c = java.net.URL("http://$h:11434/api/tags").openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 1500; c.readTimeout = 3000
                        val liste = JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("models") ?: org.json.JSONArray()
                        val modeles = org.json.JSONArray()
                        for (i in 0 until liste.length()) modeles.put(liste.getJSONObject(i).optString("name"))
                        val nom = if (h == "127.0.0.1") "ce téléphone" else try { java.net.InetAddress.getByName(h).canonicalHostName.takeIf { it != h } ?: h } catch (_: Exception) { h }
                        val o = JSONObject().put("url", "http://$h:11434").put("nom", nom).put("modeles", modeles)
                        js("lexosIA.trouve(" + o.toString() + ")")
                    } catch (_: Exception) {}
                }
                pool.shutdown()
                pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)
                js("lexosIA.scanFin(" + JSONObject.quote(ip ?: "") + ")")
            }.start()
        }

        /** IA locale : parle à Ollama (Termux sur le téléphone, ou l'ordi sur le wifi) en continu. */
        @JavascriptInterface
        fun iaDemander(url: String, modele: String, messages: String) {
            Thread {
                try {
                    val base = url.trimEnd('/')
                    var m = modele
                    if (m.isEmpty()) {
                        val t = java.net.URL("$base/api/tags").openConnection() as java.net.HttpURLConnection
                        t.connectTimeout = 4000; t.readTimeout = 8000
                        val liste = JSONObject(t.inputStream.bufferedReader().readText()).optJSONArray("models")
                        if (liste == null || liste.length() == 0) {
                            js("lexosIA.erreur(" + JSONObject.quote("Ollama répond, mais aucun modèle n'est installé.") + ")"); return@Thread
                        }
                        m = liste.getJSONObject(0).getString("name")
                    }
                    js("lexosIA.modele(" + JSONObject.quote(m) + ")")
                    val c = java.net.URL("$base/api/chat").openConnection() as java.net.HttpURLConnection
                    c.requestMethod = "POST"; c.doOutput = true
                    c.connectTimeout = 4000; c.readTimeout = 180000
                    c.setRequestProperty("Content-Type", "application/json")
                    val corps = JSONObject().put("model", m).put("messages", org.json.JSONArray(messages)).put("stream", true)
                    c.outputStream.use { it.write(corps.toString().toByteArray()) }
                    if (c.responseCode !in 200..299) {
                        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
                        js("lexosIA.erreur(" + JSONObject.quote("Ollama : " + c.responseCode + " " + err.take(160)) + ")"); return@Thread
                    }
                    c.inputStream.bufferedReader().useLines { lignes ->
                        for (l in lignes) {
                            if (l.isBlank()) continue
                            val o = JSONObject(l)
                            if (o.has("error")) { js("lexosIA.erreur(" + JSONObject.quote(o.optString("error")) + ")"); return@useLines }
                            val morceau = o.optJSONObject("message")?.optString("content") ?: ""
                            if (morceau.isNotEmpty()) js("lexosIA.morceau(" + JSONObject.quote(morceau) + ")")
                            if (o.optBoolean("done")) break
                        }
                    }
                    js("lexosIA.fin()")
                } catch (e: Exception) {
                    js("lexosIA.erreur(" + JSONObject.quote("Ollama ne répond pas à $url (" + e.javaClass.simpleName + ").") + ")")
                }
            }.start()
        }


        @JavascriptInterface
        fun batterieInfo(): String = EtatSysteme.batterie(this@MainActivity)

        @JavascriptInterface
        fun reseau(): String = EtatSysteme.reseau(this@MainActivity)

        @JavascriptInterface
        fun sante(): String = EtatSysteme.sante(this@MainActivity)

        @JavascriptInterface
        fun version(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }

        /** Ouvre le choix de l'appli d'accueil (LexOS PRO ou One UI). */
        @JavascriptInterface
        fun appliAccueil() {
            runOnUiThread {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }

        @JavascriptInterface
        fun agendaPermis(): Boolean =
            checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        @JavascriptInterface
        fun demanderAgenda() {
            runOnUiThread { requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 8) }
        }

        /** Essai : bannière par-dessus les applis avec un nom. */
        @JavascriptInterface
        fun testBanniere(type: String, nom: String) {
            Banniere.montrer(this@MainActivity, type, nom, null)
        }

        @JavascriptInterface
        fun vibrate() {
            runOnUiThread { web.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        }
    }
}
