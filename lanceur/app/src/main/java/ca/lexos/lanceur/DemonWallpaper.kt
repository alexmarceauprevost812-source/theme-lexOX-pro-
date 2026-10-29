package ca.lexos.lanceur

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Point(var x: Float, var y: Float, val v: Float, val r: Float, val a: Float)

/** Fond d'écran animé : yeux du démon entièrement rouges, clignements, braises, colère au toucher. */
class DemonWallpaper : WallpaperService() {

    override fun onCreateEngine(): Engine = Moteur()

    inner class Moteur : Engine() {
        private val main = Handler(Looper.getMainLooper())
        private var visible = false
        private var w = 0f
        private var h = 0f
        private var colereJusqua = 0L
        private val debut = SystemClock.uptimeMillis()

        private val rouge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(224, 38, 43)
            style = Paint.Style.FILL
        }
        private val lueur = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val braise = Paint(Paint.ANTI_ALIAS_FLAG)
        private val noir = Paint().apply { color = Color.BLACK }

        private val points = List(45) {
            Point(Random.nextFloat(), Random.nextFloat(), 0.0006f + Random.nextFloat() * 0.0016f, Random.nextFloat() * 1.8f + 0.4f, Random.nextFloat())
        }

        private val image = object : Runnable {
            override fun run() { dessiner(); if (visible) main.postDelayed(this, 33) }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            main.removeCallbacks(image)
            if (v) main.post(image)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width.toFloat(); h = height.toFloat()
            dessiner()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            visible = false
            main.removeCallbacks(image)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            main.removeCallbacks(image)
            super.onDestroy()
        }

        override fun onTouchEvent(event: MotionEvent?) {
            if (event?.action == MotionEvent.ACTION_DOWN) {
                val cy = h * 0.45f
                if (event.y > cy - h * 0.08f && event.y < cy + h * 0.08f) colereJusqua = SystemClock.uptimeMillis() + 1400
            }
        }

        /** Un œil : même forme que dans le lanceur (viewBox 120×62). */
        private fun oeil(cx: Float, cy: Float, echelle: Float, miroir: Boolean): Path {
            val s = if (miroir) -1f else 1f
            fun X(v: Float) = cx + (v - 60f) * echelle * s
            fun Y(v: Float) = cy + (v - 31f) * echelle
            return Path().apply {
                moveTo(X(2f), Y(4f))
                quadTo(X(60f), Y(-2f), X(118f), Y(28f))
                quadTo(X(70f), Y(70f), X(2f), Y(4f))
                close()
            }
        }

        private fun dessiner() {
            val holder = surfaceHolder ?: return
            var c: Canvas? = null
            try {
                c = holder.lockCanvas() ?: return
                val t = SystemClock.uptimeMillis() - debut
                val maintenant = SystemClock.uptimeMillis()
                val colere = maintenant < colereJusqua

                c.drawRect(0f, 0f, w, h, noir)

                // Braises
                for (p in points) {
                    p.y -= p.v
                    p.x += sin(p.y * 20 + p.a * 9) * 0.0004f
                    if (p.y < 0) { p.y = 1f; p.x = Random.nextFloat() }
                    val alpha = ((0.15f + p.a * 0.5f * p.y) * 255).toInt().coerceIn(0, 255)
                    braise.color = Color.argb(alpha, 224, (38 + p.a * 40).toInt(), 43)
                    c.drawCircle(p.x * w, p.y * h, p.r * 2.5f, braise)
                }

                // Yeux
                val echelle = min(w, h) / 380f * (if (colere) 1.08f else 1f)
                val ecart = 82f * echelle
                val cy = h * 0.45f
                val trem = if (colere) (if ((t / 40) % 2 == 0L) 3f else -3f) else 0f
                val gauche = oeil(w / 2 - ecart + trem, cy, echelle, false)
                val droit = oeil(w / 2 + ecart + trem, cy, echelle, true)

                // Lueur qui respire
                val resp = (sin(t / 510.0) * 0.5 + 0.5).toFloat()
                val intensite = if (colere) 1f else 0.45f + resp * 0.35f
                lueur.color = Color.argb((90 * intensite).toInt(), 224, 38, 43)
                lueur.maskFilter = android.graphics.BlurMaskFilter(40f * echelle, android.graphics.BlurMaskFilter.Blur.NORMAL)
                c.drawPath(gauche, lueur); c.drawPath(droit, lueur)

                rouge.color = if (colere) Color.rgb(255, 59, 59) else Color.rgb(224, 38, 43)
                c.drawPath(gauche, rouge); c.drawPath(droit, rouge)

                // Clignement toutes les 5,5 s
                val cycle = t % 5500
                val ferme = when {
                    cycle in 5060L..5225L -> (cycle - 5060) / 165f
                    cycle in 5226L..5390L -> 1f - (cycle - 5226) / 165f
                    else -> 0f
                }
                if (ferme > 0f) {
                    val haut = cy - 31f * echelle
                    val bas = haut + 66f * echelle * max(0f, ferme)
                    c.save()
                    c.clipRect(0f, haut, w, bas)
                    c.drawPath(gauche, noir); c.drawPath(droit, noir)
                    c.restore()
                }
            } catch (_: Exception) {
            } finally {
                if (c != null) try { holder.unlockCanvasAndPost(c) } catch (_: Exception) {}
            }
        }
    }
}
