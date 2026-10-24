package ca.lexos.lanceur

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

/**
 * Clavier LexOS PRO : fond noir, touches gris mat, touches spéciales orange,
 * rangée terminal (esc, tab, ctrl, flèches…), bulle d'aperçu, accents en appui long.
 */
@SuppressLint("ViewConstructor")
class ClavierView(ctx: Context) : View(ctx) {

    var service: ClavierService? = null

    // ----- couleurs du thème -----
    private val NOIR = Color.BLACK
    private val MAT = Color.rgb(0x12, 0x12, 0x12)
    private val MAT2 = Color.rgb(0x1C, 0x1C, 0x1E)
    private val LIGNE = Color.rgb(0x2A, 0x2A, 0x2A)
    private val BLANC = Color.rgb(0xED, 0xED, 0xED)
    private val GRIS = Color.rgb(0x9A, 0x9A, 0x9A)
    private val ORANGE = Color.rgb(0xE8, 0x59, 0x0C)
    private val BOUTON = Color.rgb(0xC8, 0x4B, 0x0A)
    private val NEON = Color.rgb(0xFF, 0x7A, 0x1A)   // lettres : orange néon gamer LexOS
    private val LIME = Color.rgb(0xA6, 0xFF, 0x00)   // chiffres : vert lime
    private val OR = Color.rgb(0xFF, 0xC5, 0x3D)     // symboles : jaune or

    /** Couleur d'un caractère : lettre orange, chiffre vert lime, symbole jaune or. */
    private fun couleurDe(lab: String): Int {
        val ch = lab.firstOrNull() ?: return NEON
        return when {
            lab.length == 1 && ch.isDigit() -> LIME
            lab.length == 1 && ch.isLetter() -> NEON
            lab.length == 1 -> OR
            else -> NEON
        }
    }

    // ----- codes -----
    private companion object {
        const val TEXTE = 0; const val MAJ = -1; const val SUPPR = -2; const val ENTREE = -3; const val ESPACE = -4
        const val SYMB = -5; const val ABC = -6; const val CTRL = -7; const val ESC = -8; const val TAB = -9
        const val GAUCHE = -10; const val HAUT = -11; const val BAS = -12; const val DROITE = -13; const val SYMB2 = -14
        const val GLOBE = -15
    }

    private class Touche(val label: String, val sortie: String?, val code: Int, val poids: Float = 1f, val terminal: Boolean = false) {
        val r = RectF()
        var eclat = 0f
        var appuyee = false
    }

    private val d = resources.displayMetrics.density
    private fun dp(v: Float) = v * d

    private val pFond = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBord = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1f) }
    private val pTexte = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }

    private var mode = 0          // 0 lettres, 1 symboles, 2 symboles 2
    private var maj = 0           // 0 non, 1 une fois, 2 verrouillé
    private var ctrl = false
    private var derniereMaj = 0L
    private var curseur = true
    private var rangees: List<List<Touche>> = emptyList()
    private var dernierFrame = 0L

    // appui long / accents
    private val h = Handler(Looper.getMainLooper())
    private var toucheLongue: Touche? = null
    private var accents: List<String> = emptyList()
    private var accentChoisi = 0
    private val rAccents = RectF()

    private val ACCENTS = mapOf(
        "e" to listOf("é", "è", "ê", "ë", "€"), "a" to listOf("à", "â", "æ", "ä"), "c" to listOf("ç", "©"),
        "i" to listOf("î", "ï", "ì"), "o" to listOf("ô", "œ", "ö", "ò"), "u" to listOf("ù", "û", "ü"),
        "y" to listOf("ÿ"), "n" to listOf("ñ"), "s" to listOf("ß", "$"),
        "." to listOf(",", "?", "!", "'", "\"", ":", ";", "…"), "-" to listOf("_", "—", "–")
    )
    private val CHIFFRES = "qwertyuiop"

    init { construire() }

    fun preparer(chiffres: Boolean) {
        mode = if (chiffres) 1 else 0
        maj = 0; ctrl = false
        construire(); requestLayout(); invalidate()
    }

    // ---------- dispositions ----------
    private fun rangeeTerminal() = listOf(
        Touche("esc", null, ESC, 1f, true), Touche("tab", null, TAB, 1f, true), Touche("ctrl", null, CTRL, 1.1f, true),
        Touche("~", "~", TEXTE, 0.8f, true), Touche("/", "/", TEXTE, 0.8f, true), Touche("|", "|", TEXTE, 0.8f, true),
        Touche("-", "-", TEXTE, 0.8f, true), Touche("←", null, GAUCHE, 0.9f, true), Touche("↑", null, HAUT, 0.9f, true),
        Touche("↓", null, BAS, 0.9f, true), Touche("→", null, DROITE, 0.9f, true)
    )

    private fun lettres(s: String) = s.map { Touche(it.toString(), it.toString(), TEXTE) }
    private fun symboles(s: String) = s.map { Touche(it.toString(), it.toString(), TEXTE) }

    private fun construire() {
        val bas = listOf(
            Touche(if (mode == 0) "?123" else "abc", null, if (mode == 0) SYMB else ABC, 1.5f),
            Touche("⌨", null, GLOBE, 1f),
            Touche(",", ",", TEXTE, 1f),
            Touche("lexos pro", " ", ESPACE, 4f),
            Touche(".", ".", TEXTE, 1f),
            Touche("⏎", null, ENTREE, 1.5f)
        )
        rangees = when (mode) {
            0 -> listOf(
                rangeeTerminal(),
                lettres("qwertyuiop"),
                lettres("asdfghjkl"),
                listOf(Touche("⇧", null, MAJ, 1.5f)) + lettres("zxcvbnm") + listOf(Touche("⌫", null, SUPPR, 1.5f)),
                bas
            )
            1 -> listOf(
                rangeeTerminal(),
                symboles("1234567890"),
                symboles("@#\$_&-+()/"),
                listOf(Touche("=\\<", null, SYMB2, 1.5f)) + symboles("*\"':;!?") + listOf(Touche("⌫", null, SUPPR, 1.5f)),
                bas
            )
            else -> listOf(
                rangeeTerminal(),
                symboles("~`|•±¤¨÷×¶"),
                symboles("€£¥^°={}\\%"),
                listOf(Touche("?123", null, SYMB, 1.5f)) + symboles("©®™[]<>") + listOf(Touche("⌫", null, SUPPR, 1.5f)),
                bas
            )
        }
        disposer()
    }

    private val hTerm get() = dp(40f)
    private val hRangee get() = dp(52f)
    private val marge get() = dp(4f)
    private val ecart get() = dp(5f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hTot = marge * 2 + hTerm + (rangees.size - 1) * hRangee + dp(6f)
        setMeasuredDimension(w, hTot.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { disposer() }

    private fun disposer() {
        val w = width.toFloat(); if (w <= 0f) return
        var y = marge
        rangees.forEachIndexed { i, r ->
            val hr = if (i == 0) hTerm else hRangee
            val total = r.sumOf { it.poids.toDouble() }.toFloat()
            // la rangée « asdf… » est centrée (9 touches)
            val decal = if (mode == 0 && i == 2) (w - marge * 2) / 10f / 2f else 0f
            val dispo = w - marge * 2 - decal * 2
            var x = marge + decal
            r.forEach { t ->
                val lw = dispo * t.poids / (if (mode == 0 && i == 2) 9f else total)
                t.r.set(x + ecart / 2, y + ecart / 2, x + lw - ecart / 2, y + hr - ecart / 2)
                x += lw
            }
            y += hr
        }
    }

    // ---------- dessin ----------
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        h.post(clignote)
    }

    override fun onDetachedFromWindow() {
        h.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private val clignote = object : Runnable {
        override fun run() { curseur = !curseur; invalidate(); h.postDelayed(this, 530) }
    }

    private fun majuscule(s: String) = if (mode == 0 && maj > 0) s.uppercase() else s

    override fun onDraw(c: Canvas) {
        c.drawColor(NOIR)
        val now = SystemClock.uptimeMillis()
        val dt = if (dernierFrame == 0L) 16L else (now - dernierFrame).coerceAtMost(64)
        dernierFrame = now
        var anime = false
        val rayon = dp(10f)   // (remplacé par une forme ronde uniforme ci-dessous)

        for ((i, r) in rangees.withIndex()) for (t in r) {
            if (!t.appuyee && t.eclat > 0f) { t.eclat = (t.eclat - dt / 220f).coerceAtLeast(0f); anime = true }
            val speciale = t.code != TEXTE && t.code != ESPACE
            val active = (t.code == MAJ && maj > 0) || (t.code == CTRL && ctrl)
            // thème noir : chaque touche est encerclée, même forme ronde partout
            val fond = when {
                t.code == ENTREE -> BOUTON
                active -> BOUTON
                else -> Color.rgb(0x05, 0x05, 0x05)
            }
            pFond.color = fond
            val r2 = RectF(t.r)
            if (t.eclat > 0f) { val k = dp(1.5f) * t.eclat; r2.inset(k, k) }
            val rond = minOf(r2.width(), r2.height()) / 2f
            c.drawRoundRect(r2, rond, rond, pFond)
            val coul = couleurDe(t.label)
            val bordBase = if (t.code == TEXTE && !t.terminal) Color.argb(150, Color.red(coul), Color.green(coul), Color.blue(coul)) else Color.rgb(0x3A, 0x3A, 0x3A)
            pBord.color = if (t.eclat > 0f) melange(bordBase, coul, t.eclat) else bordBase
            pBord.strokeWidth = dp(1.4f) + dp(1.6f) * t.eclat
            c.drawRoundRect(r2, rond, rond, pBord)
            if (t.eclat > 0f) {   // halo orange néon
                pFond.color = Color.argb((70 * t.eclat).toInt(), Color.red(coul), Color.green(coul), Color.blue(coul))
                c.drawRoundRect(r2, rond, rond, pFond)
            }
            // texte
            pTexte.color = when {
                t.code == ENTREE || active -> Color.WHITE
                t.terminal -> couleurDe(t.label)
                t.code == ESPACE -> GRIS
                speciale -> GRIS
                else -> couleurDe(t.label)   // lettre orange, chiffre lime, symbole or
            }
            // lueur néon de la même couleur que le caractère
            if (t.code == TEXTE || t.terminal) pTexte.setShadowLayer(dp(5f) + dp(4f) * t.eclat, 0f, 0f, pTexte.color)
            else pTexte.clearShadowLayer()
            pTexte.textSize = when {
                t.terminal -> dp(13f)
                t.code == ESPACE -> dp(12f)
                t.label.length > 1 && !speciale -> dp(13f)
                speciale && t.label.length > 2 -> dp(13f)
                else -> dp(20f)
            }
            var lab = if (t.code == TEXTE) majuscule(t.label) else t.label
            if (t.code == MAJ && maj == 2) lab = "⇪"
            if (t.code == ESPACE) lab = "lexos pro" + if (curseur) "▮" else " "
            val ty = r2.centerY() - (pTexte.descent() + pTexte.ascent()) / 2
            c.drawText(lab, r2.centerX(), ty, pTexte)
            pTexte.clearShadowLayer()
            // petit chiffre en appui long sur la rangée du haut
            if (mode == 0 && i == 1 && t.code == TEXTE) {
                pTexte.textSize = dp(9f); pTexte.color = LIME
                c.drawText((CHIFFRES.indexOf(t.label) + 1).mod(10).toString(), r2.right - rond * 0.55f, r2.top + dp(12f), pTexte)
            }
            if (t.code == ESPACE) {   // trait orange qui respire
                pFond.color = Color.argb(140 + (if (curseur) 80 else 0), 0xE8, 0x59, 0x0C)
                c.drawRoundRect(r2.centerX() - dp(26f), r2.bottom - dp(7f), r2.centerX() + dp(26f), r2.bottom - dp(5f), dp(1f), dp(1f), pFond)
            }
        }

        // bulle d'aperçu au-dessus de la touche appuyée
        for (r in rangees) for (t in r) if (t.appuyee && t.code == TEXTE && !t.terminal && toucheLongue == null) {
            val bw = t.r.width() * 1.3f; val bh = hRangee * 1.15f
            val b = RectF(t.r.centerX() - bw / 2, t.r.top - bh - dp(4f), t.r.centerX() + bw / 2, t.r.top - dp(4f))
            if (b.left < 0) b.offset(-b.left + dp(2f), 0f); if (b.right > width) b.offset(width - b.right - dp(2f), 0f)
            if (b.top < 0f) b.top = 0f
            pFond.color = Color.rgb(0x0A, 0x0A, 0x0A); c.drawRoundRect(b, dp(12f), dp(12f), pFond)
            pBord.color = ORANGE; pBord.strokeWidth = dp(2f); c.drawRoundRect(b, dp(12f), dp(12f), pBord)
            pTexte.color = couleurDe(t.label); pTexte.textSize = dp(30f); pTexte.setShadowLayer(dp(10f), 0f, 0f, pTexte.color)
            c.drawText(majuscule(t.label), b.centerX(), b.centerY() - (pTexte.descent() + pTexte.ascent()) / 2, pTexte)
            pTexte.clearShadowLayer()
        }

        // barre d'accents en appui long
        val tl = toucheLongue
        if (tl != null && accents.isNotEmpty()) {
            val cw = dp(40f); val bw = cw * accents.size + dp(8f); val bh = hRangee
            rAccents.set(tl.r.centerX() - bw / 2, tl.r.top - bh - dp(6f), tl.r.centerX() + bw / 2, tl.r.top - dp(6f))
            if (rAccents.left < 0) rAccents.offset(-rAccents.left + dp(2f), 0f)
            if (rAccents.right > width) rAccents.offset(width - rAccents.right - dp(2f), 0f)
            if (rAccents.top < 0f) rAccents.offset(0f, -rAccents.top)
            pFond.color = Color.rgb(0x0A, 0x0A, 0x0A); c.drawRoundRect(rAccents, dp(12f), dp(12f), pFond)
            pBord.color = ORANGE; pBord.strokeWidth = dp(2f); c.drawRoundRect(rAccents, dp(12f), dp(12f), pBord)
            accents.forEachIndexed { k, a ->
                val cx = rAccents.left + dp(4f) + cw * k + cw / 2
                if (k == accentChoisi) {
                    pFond.color = BOUTON
                    c.drawRoundRect(cx - cw / 2 + dp(2f), rAccents.top + dp(5f), cx + cw / 2 - dp(2f), rAccents.bottom - dp(5f), dp(8f), dp(8f), pFond)
                }
                pTexte.color = if (k == accentChoisi) Color.WHITE else couleurDe(a); pTexte.textSize = dp(20f)
                c.drawText(majuscule(a), cx, rAccents.centerY() - (pTexte.descent() + pTexte.ascent()) / 2, pTexte)
            }
        }
        if (anime) postInvalidateOnAnimation() else dernierFrame = 0L
    }

    private fun melange(a: Int, b: Int, k: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
    )

    // ---------- toucher ----------
    private val actives = HashMap<Int, Touche>()

    private fun trouver(x: Float, y: Float): Touche? {
        var best: Touche? = null; var dist = Float.MAX_VALUE
        for (r in rangees) for (t in r) {
            if (t.r.contains(x, y)) return t
            val dx = maxOf(t.r.left - x, 0f, x - t.r.right); val dy = maxOf(t.r.top - y, 0f, y - t.r.bottom)
            val dd = dx * dx + dy * dy
            if (dd < dist) { dist = dd; best = t }
        }
        return if (dist < dp(12f) * dp(12f)) best else null
    }

    private val repete = object : Runnable {
        override fun run() { executer(SUPPR, null); h.postDelayed(this, 55) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // une 2e touche valide la 1re (frappe rapide)
                if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    for ((pid, t) in actives.entries.toList()) if (t.code == TEXTE && toucheLongue == null) {
                        relacher(t); actives.remove(pid)
                    }
                }
                val t = trouver(e.getX(idx), e.getY(idx)) ?: return true
                t.appuyee = true; t.eclat = 1f; actives[id] = t
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                h.removeCallbacks(repete)
                if (t.code == SUPPR) { executer(SUPPR, null); h.postDelayed(repete, 400) }
                val alts = alternatives(t)
                if (alts.isNotEmpty()) h.postDelayed({
                    if (t.appuyee && actives.containsValue(t)) {
                        toucheLongue = t; accents = alts; accentChoisi = 0
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); invalidate()
                    }
                }, 380)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (toucheLongue != null && accents.isNotEmpty()) {
                    val cw = dp(40f)
                    val k = ((e.getX(0) - rAccents.left - dp(4f)) / cw).toInt().coerceIn(0, accents.size - 1)
                    if (k != accentChoisi) { accentChoisi = k; invalidate() }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val t = actives.remove(id)
                if (t != null) relacher(t)
            }
            MotionEvent.ACTION_CANCEL -> {
                actives.values.forEach { it.appuyee = false }; actives.clear(); toucheLongue = null
                h.removeCallbacks(repete); invalidate()
            }
        }
        return true
    }

    private fun relacher(t: Touche) {
        t.appuyee = false
        h.removeCallbacks(repete)
        val tl = toucheLongue
        if (tl === t) {
            toucheLongue = null
            accents.getOrNull(accentChoisi)?.let { ecrire(it) }
            accents = emptyList()
        } else if (t.code != SUPPR) {
            executer(t.code, t.sortie)
        }
        invalidate()
    }

    private fun alternatives(t: Touche): List<String> {
        if (t.code != TEXTE || t.terminal) return emptyList()
        val base = ACCENTS[t.label] ?: emptyList()
        val chiffre = if (mode == 0 && CHIFFRES.contains(t.label)) listOf(((CHIFFRES.indexOf(t.label) + 1) % 10).toString()) else emptyList()
        return chiffre + base
    }

    private fun ecrire(s: String) {
        val sortie = majuscule(s)
        if (ctrl && sortie.length == 1 && sortie[0].isLetter()) {
            service?.ctrl(sortie[0]); ctrl = false
        } else service?.ecrire(sortie)
        if (maj == 1) { maj = 0 }
    }

    private fun executer(code: Int, sortie: String?) {
        val s = service ?: return
        when (code) {
            TEXTE -> if (sortie != null) ecrire(sortie)
            ESPACE -> { s.ecrire(" "); if (maj == 1) maj = 0 }
            SUPPR -> s.effacer()
            ENTREE -> s.entree()
            MAJ -> {
                val t = SystemClock.uptimeMillis()
                maj = when {
                    maj == 0 && t - derniereMaj < 350 -> 2
                    maj == 0 -> 1
                    maj == 1 && t - derniereMaj < 350 -> 2
                    else -> 0
                }
                derniereMaj = t
            }
            SYMB -> { mode = 1; construire() }
            SYMB2 -> { mode = 2; construire() }
            ABC -> { mode = 0; construire() }
            CTRL -> ctrl = !ctrl
            ESC -> s.touche(KeyEvent.KEYCODE_ESCAPE)
            TAB -> s.touche(KeyEvent.KEYCODE_TAB)
            GAUCHE -> s.touche(KeyEvent.KEYCODE_DPAD_LEFT)
            HAUT -> s.touche(KeyEvent.KEYCODE_DPAD_UP)
            BAS -> s.touche(KeyEvent.KEYCODE_DPAD_DOWN)
            DROITE -> s.touche(KeyEvent.KEYCODE_DPAD_RIGHT)
            GLOBE -> s.changerClavier()
        }
        invalidate()
    }
}
