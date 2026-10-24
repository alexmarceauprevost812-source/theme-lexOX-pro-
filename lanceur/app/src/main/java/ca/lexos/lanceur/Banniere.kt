package ca.lexos.lanceur

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bannière « terminal LexOS PRO v4 » affichée par-dessus les autres applis
 * quand un SMS ou un appel arrive : le nom du contact en grosses lettres █
 * qui apparaissent ligne par ligne, avec un scintillement de vieil écran.
 */
object Banniere {
    private val ORANGE = Color.rgb(255, 140, 0)
    private val VERT = Color.rgb(57, 255, 136)
    private val GRIS = Color.rgb(154, 154, 154)
    private val SPIN = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"

    private var vueActuelle: View? = null
    private val h = Handler(Looper.getMainLooper())

    fun peutAfficher(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** type = "sms" ou "appel" */
    fun montrer(ctx: Context, type: String, nom: String, pkg: String?) {
        if (!peutAfficher(ctx)) return
        h.post { afficher(ctx.applicationContext, type, nom, pkg) }
    }

    private fun sp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    private fun dp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    private fun retirer(ctx: Context) {
        val v = vueActuelle ?: return
        vueActuelle = null
        h.removeCallbacksAndMessages(null)
        try { (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
    }

    private fun afficher(ctx: Context, type: String, nom: String, pkg: String?) {
        retirer(ctx)
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val largeur = ctx.resources.displayMetrics.widthPixels - dp(ctx, 24f)
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        val boite = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 16f), dp(ctx, 12f), dp(ctx, 16f), dp(ctx, 14f))
            background = GradientDrawable().apply {
                setColor(Color.argb(245, 0, 0, 0))
                cornerRadius = dp(ctx, 18f).toFloat()
                setStroke(dp(ctx, 2f), ORANGE)
            }
            elevation = dp(ctx, 12f).toFloat()
        }
        val tete = TextView(ctx).apply { typeface = mono; setTextColor(VERT); textSize = 12f }
        val bloc = TextView(ctx).apply {
            typeface = mono; setTextColor(ORANGE); includeFontPadding = false
            setLineSpacing(0f, 0.92f); setShadowLayer(dp(ctx, 6f).toFloat(), 0f, 0f, ORANGE)
        }
        val pied = TextView(ctx).apply { typeface = mono; setTextColor(GRIS); textSize = 11f }
        boite.addView(tete); boite.addView(bloc); boite.addView(pied)
        (bloc.layoutParams as LinearLayout.LayoutParams).setMargins(0, dp(ctx, 8f), 0, dp(ctx, 8f))

        val mot = Police5.court(nom)
        val lignes = Police5.lignes(mot)
        // taille du texte pour que le mot tienne dans la largeur
        val cols = lignes[0].length.coerceAtLeast(1)
        val dispo = largeur - dp(ctx, 32f)
        val p = android.graphics.Paint().apply { typeface = mono; textSize = 100f }
        val larg100 = p.measureText("█")
        bloc.setTextSize(TypedValue.COMPLEX_UNIT_PX, (dispo / cols / larg100 * 100f).coerceAtMost(sp(ctx, 22f)))

        val ligneTete = if (type == "appel") "☎ appel entrant — " + nom else "✉ sms de " + nom
        val lp = WindowManager.LayoutParams(
            largeur, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(ctx, 40f) }

        boite.setOnClickListener {
            retirer(ctx)
            if (pkg != null) ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(it)
            }
        }
        try { wm.addView(boite, lp) } catch (e: Exception) { return }
        vueActuelle = boite

        // entrée : glisse du haut avec rebond
        boite.translationY = -dp(ctx, 220f).toFloat()
        boite.animate().translationY(0f).setDuration(420).setInterpolator(OvershootInterpolator(1.2f)).start()

        // 1) en-tête tapé lettre par lettre
        var t = 0L
        for (i in 1..ligneTete.length) {
            h.postDelayed({ tete.text = ligneTete.substring(0, i) + "▮" }, t); t += 22
        }
        h.postDelayed({ tete.text = ligneTete }, t)
        // 2) lettres en blocs, une ligne à la fois
        for (r in 1..5) {
            h.postDelayed({ bloc.text = lignes.take(r).joinToString("\n") }, t + r * 95L)
        }
        t += 5 * 95L + 60
        // 3) scintillement de vieil écran
        listOf(0.35f, 1f, 0.6f, 1f).forEachIndexed { i, a -> h.postDelayed({ bloc.alpha = a }, t + i * 70L) }
        t += 300
        // 4) pied : TI-LEX TERMINAL PRO v4 + spinner
        val base = "TI-LEX TERMINAL PRO "
        h.postDelayed({
            val s = SpannableStringBuilder(base + "v4")
            s.setSpan(ForegroundColorSpan(ORANGE), 0, base.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(VERT), base.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            pied.text = s
        }, t)
        if (type == "appel") {
            for (k in 0 until 40) h.postDelayed({
                val s = SpannableStringBuilder(SPIN[k % SPIN.length] + " sonnerie · " + base + "v4")
                s.setSpan(ForegroundColorSpan(VERT), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                pied.text = s
            }, t + 200 + k * 90L)
        }
        val duree = if (type == "appel") t + 4200 else t + 3200
        h.postDelayed({
            boite.animate().translationY(-dp(ctx, 220f).toFloat()).alpha(0f).setDuration(300)
                .withEndAction { retirer(ctx) }.start()
        }, duree)
    }
}
