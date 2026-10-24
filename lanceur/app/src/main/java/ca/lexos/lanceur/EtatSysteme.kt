package ca.lexos.lanceur

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
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
