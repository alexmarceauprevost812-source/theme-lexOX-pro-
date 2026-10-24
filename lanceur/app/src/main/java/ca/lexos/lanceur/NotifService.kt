package ca.lexos.lanceur

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Écoute les notifications de SMS et d'appels pour afficher le nom du
 * contact avec l'animation du terminal LexOS PRO v4.
 * L'utilisateur doit autoriser « Accès aux notifications » pour LexOS PRO.
 */
class NotifService : NotificationListenerService() {

    companion object {
        val SMS = setOf(
            "com.samsung.android.messaging", "com.google.android.apps.messaging",
            "com.android.messaging", "com.textra", "org.thoughtcrime.securesms"
        )
        val APPELS = setOf(
            "com.samsung.android.incallui", "com.samsung.android.dialer",
            "com.google.android.dialer", "com.android.server.telecom", "com.android.incallui"
        )
        private var dernier = ""
        private var dernierT = 0L

        fun enregistrer(ctx: Context, type: String, nom: String, pkg: String) {
            ctx.getSharedPreferences("lexos", Context.MODE_PRIVATE).edit()
                .putString("evt_type", type).putString("evt_nom", nom).putString("evt_pkg", pkg)
                .putLong("evt_t", System.currentTimeMillis()).apply()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val estAppel = pkg in APPELS || n.category == Notification.CATEGORY_CALL
        val estSms = pkg in SMS || (n.category == Notification.CATEGORY_MESSAGE && pkg.contains("messag"))
        if (!estAppel && !estSms) return

        val ex = n.extras
        var nom = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: return
        nom = nom.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()   // « Marie (2) » → « Marie »
        if (nom.isEmpty()) return
        // pas les notifications d'appel en cours, ni de doublons rapprochés
        if (estAppel && (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 && n.category != Notification.CATEGORY_CALL) return
        val type = if (estAppel) "appel" else "sms"
        val cle = "$type|$nom"
        val maintenant = System.currentTimeMillis()
        if (cle == dernier && maintenant - dernierT < 15000) return
        dernier = cle; dernierT = maintenant

        val appli = if (estAppel) "com.samsung.android.dialer" else pkg
        enregistrer(this, type, nom, appli)
        Banniere.montrer(this, type, nom, appli)
    }
}
