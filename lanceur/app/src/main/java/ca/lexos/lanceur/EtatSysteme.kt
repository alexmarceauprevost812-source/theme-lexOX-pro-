package ca.lexos.lanceur

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.app.ActivityManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.telephony.TelephonyManager
import org.json.JSONObject

/**
 * Batterie et réseau pour les indicateurs LexOS PRO (accueil et verrouillage).
 * Aucune permission dangereuse : seulement l'état du réseau et du Wi-Fi.
 */
object EtatSysteme {

    /** {"niveau": 0-100, "charge": true/false} */
    fun batterie(ctx: Context): String {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val niveau = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val echelle = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val statut = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (niveau >= 0 && echelle > 0) niveau * 100 / echelle else
            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charge = statut == BatteryManager.BATTERY_STATUS_CHARGING || statut == BatteryManager.BATTERY_STATUS_FULL
        return JSONObject().put("niveau", pct).put("charge", charge).toString()
    }

    /** Santé de l'appareil : batterie détaillée, stockage, mémoire, chaleur, système. */
    fun sante(ctx: Context): String {
        val o = JSONObject()
        try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val niveau = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val echelle = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val sante = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "bonne"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "surchauffe"
                BatteryManager.BATTERY_HEALTH_DEAD -> "usée"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "surtension"
                BatteryManager.BATTERY_HEALTH_COLD -> "trop froide"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "défaut"
                else -> "inconnue"
            }
            val source = when (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
                BatteryManager.BATTERY_PLUGGED_AC -> "chargeur"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "sans fil"
                else -> "batterie"
            }
            val statut = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val cycles = if (Build.VERSION.SDK_INT >= 34) i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1 else -1
            o.put("batterie", JSONObject()
                .put("niveau", if (niveau >= 0 && echelle > 0) niveau * 100 / echelle else -1)
                .put("sante", sante)
                .put("temp", (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0)
                .put("tension", (i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0)
                .put("techno", i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "?")
                .put("source", source)
                .put("charge", statut == BatteryManager.BATTERY_STATUS_CHARGING || statut == BatteryManager.BATTERY_STATUS_FULL)
                .put("cycles", cycles))
        } catch (_: Exception) {}
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            o.put("stockage", JSONObject().put("total", st.totalBytes).put("libre", st.availableBytes))
        } catch (_: Exception) {}
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
            o.put("memoire", JSONObject().put("total", mi.totalMem).put("libre", mi.availMem).put("faible", mi.lowMemory))
        } catch (_: Exception) {}
        val chaleur = if (Build.VERSION.SDK_INT >= 29) {
            when ((ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "normale"
                PowerManager.THERMAL_STATUS_LIGHT -> "tiède"
                PowerManager.THERMAL_STATUS_MODERATE -> "chaude"
                PowerManager.THERMAL_STATUS_SEVERE -> "très chaude"
                else -> "critique"
            }
        } else "?"
        o.put("systeme", JSONObject()
            .put("modele", (Build.MANUFACTURER ?: "").replaceFirstChar { it.uppercase() } + " " + (Build.MODEL ?: ""))
            .put("android", Build.VERSION.RELEASE ?: "?")
            .put("coeurs", Runtime.getRuntime().availableProcessors())
            .put("allume", SystemClock.elapsedRealtime() / 1000)
            .put("chaleur", chaleur))
        return o.toString()
    }

    /** {"type": "wifi" | "cell" | "aucun", "force": 0-4} */
    fun reseau(ctx: Context): String {
        val o = JSONObject().put("type", "aucun").put("force", 0)
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return o.toString()
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    o.put("type", "wifi")
                    @Suppress("DEPRECATION")
                    val rssi = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.rssi
                    @Suppress("DEPRECATION")
                    o.put("force", WifiManager.calculateSignalLevel(rssi, 5).coerceIn(0, 4))
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    o.put("type", "cell")
                    val force = if (Build.VERSION.SDK_INT >= 28) {
                        (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).signalStrength?.level ?: 2
                    } else 2
                    o.put("force", force.coerceIn(0, 4))
                }
                else -> { o.put("type", "autre"); o.put("force", 3) }
            }
        } catch (_: Exception) {}
        return o.toString()
    }
}
