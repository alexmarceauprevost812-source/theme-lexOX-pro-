package ca.lexos.lanceur

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings

/**
 * Au moment où l'écran s'éteint, on prépare l'écran de verrouillage LexOS
 * pour qu'il soit là dès que l'écran se rallume.
 * (Lancer un écran depuis l'arrière-plan demande l'autorisation
 *  « S'afficher par-dessus les autres applis ».)
 */
class LexOSApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (i.action != Intent.ACTION_SCREEN_OFF) return
                if (!verrouActif(c) || !Settings.canDrawOverlays(c)) return
                try {
                    c.startActivity(Intent(c, VerrouActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    ))
                } catch (_: Exception) {}
            }
        }, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    companion object {
        fun verrouActif(c: Context) = c.getSharedPreferences("lexos", Context.MODE_PRIVATE).getBoolean("verrou", true)
    }
}
