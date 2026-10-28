package com.sutra.app
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** A hashira dashes in, slashes a demon to ash, stands in a glowing aura with embers, then leaves. */
class HashiraView(c: Context, private val bmp: Bitmap, private val aura: Int) : View(c) {
    companion object { const val TOTAL = 6500L }
    private class P(var x: Float, var y: Float, val vx: Float, val vy: Float, val r: Float, val life: Float, val ash: Boolean) { var age = 0f }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pts = ArrayList<P>()
    private val rnd = java.util.Random()
    private val t0 = System.currentTimeMillis()
    private var last = 0f
    private var burst = false
    private val src = Rect(0, 0, bmp.width, bmp.height)

    override fun onDraw(cv: Canvas) {
        val t = (System.currentTimeMillis() - t0) / 1000f
        val dt = (t - last).coerceIn(0f, 0.05f); last = t
        val w = width.toFloat(); val h = height.toFloat()
        val inT = (t / 0.55f).coerceIn(0f, 1f)
        val outT = ((t - 5.7f) / 0.6f).coerceIn(0f, 1f)
        val ease = 1f - (1f - inT).pow(3)
        val vis = ease * (1f - outT)
        val ch = h * 0.9f; val cw = ch * bmp.width / bmp.height
        val cx = w * 0.52f + (1f - ease) * w * 0.8f + outT * outT * w * 0.8f
        val cyy = h * 0.52f + sin(t * 2.6f) * h * 0.008f
        val scale = 0.85f + 0.15f * ease

        // aura
        p.reset(); p.isAntiAlias = true
        val pulse = 0.75f + 0.25f * sin(t * 5f)
        p.shader = RadialGradient(cx, cyy, h * 0.5f, intArrayOf(withA(aura, 0.55f * vis * pulse), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cyy, h * 0.5f, p)

        // speed lines while dashing in / out
        val dash = max(1f - inT, outT)
        if (dash > 0.02f) {
            p.reset(); p.isAntiAlias = true; p.strokeWidth = 3f; p.color = withA(Color.WHITE, 0.6f * dash)
            for (i in 0 until 9) { val y = h * (0.12f + i * 0.09f); val len = w * (0.25f + 0.2f * ((i * 37) % 5) / 5f); cv.drawLine(cx + cw * 0.3f, y, cx + cw * 0.3f + len * dash, y, p) }
        }

        // character
        val dw = cw * scale; val dh = ch * scale
        p.reset(); p.isAntiAlias = true; p.isFilterBitmap = true; p.alpha = (255 * vis).toInt()
        cv.drawBitmap(bmp, src, RectF(cx - dw / 2, cyy - dh / 2, cx + dw / 2, cyy + dh / 2), p)

        // slash across the demon
        val s = ((t - 0.5f) / 0.3f)
        if (s in 0f..1.4f) {
            val q = s.coerceIn(0f, 1f); val fade = (1.4f - s).coerceIn(0f, 1f)
            for (k in 0 until 3) {
                val x0 = w * (0.02f + 0.03f * k); val y0 = h * (0.22f + 0.1f * k)
                val x1 = x0 + w * 0.7f * q; val y1 = y0 + h * 0.4f * q
                p.reset(); p.isAntiAlias = true; p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = 16f; p.color = withA(aura, 0.35f * fade); cv.drawLine(x0, y0, x1, y1, p)
                p.strokeWidth = 4f; p.color = withA(Color.WHITE, 0.95f * fade); cv.drawLine(x0, y0, x1, y1, p)
            }
        }
        if (!burst && t > 0.7f) {   // the demon turns to ash
            burst = true
            for (i in 0 until 42) { val a = rnd.nextFloat() * 6.283f; val sp = 40f + rnd.nextFloat() * 260f
                pts += P(w * 0.3f, h * 0.45f, cos(a) * sp, sin(a) * sp - 30f, 2f + rnd.nextFloat() * 4f, 0.9f + rnd.nextFloat() * 0.9f, true) }
        }
        // embers rising around him
        if (t < 5.6f && pts.size < 140 && rnd.nextInt(100) < 70)
            pts += P(cx + (rnd.nextFloat() - .5f) * cw * 0.8f, cyy + ch * 0.35f, (rnd.nextFloat() - .5f) * 30f, -(50f + rnd.nextFloat() * 90f), 1.5f + rnd.nextFloat() * 2.5f, 1.2f + rnd.nextFloat(), false)
        val it = pts.iterator()
        while (it.hasNext()) {
            val q = it.next(); q.age += dt; if (q.age > q.life) { it.remove(); continue }
            q.x += q.vx * dt; q.y += q.vy * dt
            val a = 1f - q.age / q.life
            p.reset(); p.isAntiAlias = true
            p.color = if (q.ash) withA(0xFF2A1233.toInt(), 0.85f * a) else withA(aura, a * 0.9f)
            cv.drawCircle(q.x, q.y, q.r * (if (q.ash) 1f + (1f - a) else a + 0.4f), p)
        }
        if (t < TOTAL / 1000f) postInvalidateOnAnimation()
    }
    private fun withA(c: Int, a: Float) = (c and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
}
