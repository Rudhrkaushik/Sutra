package com.sutra.app
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Siri-style orb: layered flowing ribbons inside a glowing sphere. */
class OrbView(c: Context) : View(c) {
    enum class S { IDLE, LISTEN, THINK, SPEAK, SLEEP }
    var state = S.IDLE
    var level = 0f            // 0..1 microphone loudness
    private var lv = 0f; private var t = 0f; private var gray = 0f
    private val cols = intArrayOf(0xFF2E9BFF.toInt(), 0xFFB44CFF.toInt(), 0xFFFF4DA6.toInt(), 0xFF2EE6D0.toInt())
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val path = Path()

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2; val cy = h / 2
        val r = min(w, h) / 2 * 0.86f
        val target = when (state) {
            S.LISTEN -> 0.18f + level * 0.9f
            S.SPEAK -> 0.35f + 0.3f * (0.5f + 0.5f * sin(t * 7f))
            S.THINK -> 0.3f
            S.SLEEP -> 0.1f
            else -> 0.2f
        }
        lv += (target - lv) * 0.12f
        val speed = when (state) { S.SLEEP -> 0.012f; S.LISTEN -> 0.045f; S.THINK -> 0.06f; else -> 0.025f }
        t += speed
        gray += ((if (state == S.SLEEP) 1f else 0f) - gray) * 0.08f
        // outer glow
        p.reset(); p.isAntiAlias = true
        p.shader = RadialGradient(cx, cy, r * 1.15f, intArrayOf(tint(0xFF7A5CFF.toInt(), 0.35f * (1 - gray * 0.7f)), Color.TRANSPARENT), floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r * 1.15f, p)
        // dark glass body
        p.reset(); p.isAntiAlias = true; p.color = 0xEE05060F.toInt(); cv.drawCircle(cx, cy, r, p)
        cv.save(); path.reset(); path.addCircle(cx, cy, r, Path.Direction.CW); cv.clipPath(path)
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.xfermode = add
        for (i in 0 until 4) {
            val col = mix(cols[i], 0xFF8A8D99.toInt(), gray)
            val amp = r * (0.22f + lv * 0.55f) * (0.7f + 0.15f * i)
            path.reset(); var x = -r; var first = true
            while (x <= r) {
                val u = x / r; val env = 1f - u * u
                val y = cy + sin(u * (2.2f + i * 0.8f) + t * (1.2f + i * 0.5f) + i * 1.9f) * amp * env +
                        sin(u * 5f - t * 2.1f + i) * amp * 0.25f * env
                if (first) { path.moveTo(cx + x, y); first = false } else path.lineTo(cx + x, y)
                x += r / 24f
            }
            p.color = col; p.alpha = (60 + 40 * lv).toInt(); p.strokeWidth = r * (0.5f + 0.3f * lv); cv.drawPath(path, p)
            p.alpha = 150; p.strokeWidth = r * 0.12f; cv.drawPath(path, p)
        }
        cv.restore()
        p.reset(); p.isAntiAlias = true; p.style = Paint.Style.STROKE; p.strokeWidth = max(1.5f, r * 0.03f)
        p.color = Color.argb((90 * (1 - gray * 0.5f)).toInt(), 255, 255, 255); cv.drawCircle(cx, cy, r, p)
        postInvalidateOnAnimation()
    }
    private fun tint(c: Int, a: Float) = (c and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun mix(a: Int, b: Int, k: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt())
}
