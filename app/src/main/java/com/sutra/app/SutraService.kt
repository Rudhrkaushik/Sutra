package com.sutra.app
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.os.*
import android.speech.*
import android.speech.tts.*
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.*
import org.json.JSONObject
import java.net.*
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class SutraService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var orb: OrbView
    private lateinit var lp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var sr: SpeechRecognizer? = null
    private var busy = false
    private var sleeping = false
    private var cy = 0
    private val prefs by lazy { getSharedPreferences("sutra", 0) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private val small get() = dp(64)
    private val big get() = dp(230)
    private val name get() = prefs.getString("name", "boss")!!

    private val LESSONS = listOf(
        "Do the work. Let go of the result.", "Lift yourself with your own mind.",
        "Calm in joy and pain. Both pass.", "Return to focus, again and again.",
        "Your own path, imperfect, beats another's perfect.")
    private val NUDGES = listOf("Wash your eyes, $name.", "Water. Then the hard task.", "Look far away for 20 seconds.",
        "Straighten up. Breathe. Begin.", "One more focused hour, $name.")

    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("sutra", "Sutra", NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "sutra").setContentTitle("Sutra is awake").setSmallIcon(android.R.drawable.ic_btn_speak_now).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        orb = OrbView(this)
        sleeping = prefs.getBoolean("sleep", false)
        orb.state = if (sleeping) OrbView.S.SLEEP else OrbView.S.IDLE
        cy = prefs.getInt("cy", resources.displayMetrics.heightPixels / 2)
        lp = WindowManager.LayoutParams(small, small, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END; x = dp(2); y = cy - small / 2 }
        wm.addView(orb, lp)
        touch()
        tts = TextToSpeech(this) { s ->
            if (s == TextToSpeech.SUCCESS) {
                tts!!.language = Locale.US
                // closest thing to Siri's voice on Android: a clear female US neural voice
                val v = tts!!.voices?.filter { it.locale == Locale.US && !it.isNetworkConnectionRequired }
                (v?.firstOrNull { listOf("tpf", "iob", "sfg", "iol").any { k -> it.name.contains(k) } } ?: v?.firstOrNull())?.let { tts!!.voice = it }
                tts!!.setPitch(1.02f); tts!!.setSpeechRate(0.98f); ttsOk = true
            }
        }
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { bootAnim() }
        }, IntentFilter(Intent.ACTION_USER_PRESENT), RECEIVER_EXPORTED)
        h.postDelayed({ morning() }, 4000); tick()
    }

    // ---- gestures: tap = listen, double tap = sleep/wake, drag = move up/down on the right edge
    private fun touch() {
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { if (!sleeping && !busy) listen(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { toggleSleep(); return true }
        })
        var sy = 0f; var oy = 0
        orb.setOnTouchListener { _, e ->
            gd.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sy = e.rawY; oy = cy }
                MotionEvent.ACTION_MOVE -> if (abs(e.rawY - sy) > dp(8) && !busy) { cy = (oy + e.rawY - sy).toInt(); place() }
                MotionEvent.ACTION_UP -> prefs.edit().putInt("cy", cy).apply()
            }
            true
        }
    }
    private fun place() { lp.y = cy - lp.height / 2; try { wm.updateViewLayout(orb, lp) } catch (_: Exception) {} }
    private fun resize(to: Int, ms: Long, bounce: Boolean) {
        ValueAnimator_(lp.width, to, ms, bounce) { v -> lp.width = v; lp.height = v; place() }
    }
    private fun ValueAnimator_(a: Int, b: Int, ms: Long, bounce: Boolean, f: (Int) -> Unit) {
        android.animation.ValueAnimator.ofInt(a, b).apply {
            duration = ms; if (bounce) interpolator = OvershootInterpolator(1.4f)
            addUpdateListener { f(it.animatedValue as Int) }; start() }
    }
    private fun toggleSleep() {
        sleeping = !sleeping; prefs.edit().putBoolean("sleep", sleeping).apply()
        tts?.stop(); sr?.cancel(); busy = false
        orb.state = if (sleeping) OrbView.S.SLEEP else OrbView.S.IDLE
        resize(small, 300, false)
    }

    // ---- listening
    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        busy = true; orb.state = OrbView.S.LISTEN; resize(big, 520, true)
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this)
            sr!!.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) { orb.level = ((rms + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { orb.level = 0f }
                override fun onError(e: Int) { finish() }
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onResults(r: Bundle?) {
                    val q = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (q == null) finish() else answer(q)
                }
            })
        }
        sr!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
    }
    private fun finish() { orb.level = 0f; busy = false; orb.state = if (sleeping) OrbView.S.SLEEP else OrbView.S.IDLE; resize(small, 450, false) }

    // ---- thinking and speaking
    private fun answer(q: String) {
        val l = q.lowercase(Locale.getDefault())
        orb.state = OrbView.S.THINK
        when {
            Regex("\\btime\\b").containsMatchIn(l) -> say("It's " + java.text.SimpleDateFormat("h:mm a", Locale.US).format(Date()) + ", $name.")
            Regex("\\b(date|today|my day)\\b").containsMatchIn(l) -> say("It's " + java.text.SimpleDateFormat("EEEE, d MMMM", Locale.US).format(Date()) + ", $name.")
            Regex("lesson|gita").containsMatchIn(l) -> say(LESSONS.random())
            else -> {
                say("Going through it, $name.", wait = false)
                thread {
                    val a = try { wiki(q) } catch (e: Exception) { null }
                    h.post { say(a ?: "I couldn't find a clean answer for that.") }
                }
            }
        }
    }
    private fun wiki(q: String): String? {
        val u = "https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrlimit=1&prop=extracts&exintro&explaintext&exsentences=2&format=json&gsrsearch=" + URLEncoder.encode(q, "UTF-8")
        val c = URL(u).openConnection() as HttpURLConnection; c.connectTimeout = 8000; c.readTimeout = 8000
        val pages = JSONObject(c.inputStream.bufferedReader().readText()).optJSONObject("query")?.optJSONObject("pages") ?: return null
        return pages.optJSONObject(pages.keys().next())?.optString("extract")?.replace(Regex("\\s*\\([^)]*\\)"), "")?.trim()
    }
    private fun say(text: String, wait: Boolean = true) {
        orb.state = OrbView.S.SPEAK
        if (!ttsOk) { h.postDelayed({ if (wait) finish() }, 1500); return }
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { if (wait) h.post { finish() } }
            override fun onDone(id: String?) { if (wait) h.post { finish() } else h.post { orb.state = OrbView.S.THINK } }
        })
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
    }

    // ---- rare moments: a few words, hashira pictures, wake animation
    private fun bootAnim() {
        if (sleeping || busy) return
        busy = true; resize(big, 600, true)
        h.postDelayed({ busy = false; resize(small, 500, false)
            if (Random().nextInt(100) < 40) words(greet() + ", $name.") }, 1400)
    }
    private fun greet() = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..20 -> "Good evening"; else -> "Good night" }
    private fun morning() {
        val d = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) in 5..12 && prefs.getString("lesson", "") != d) {
            prefs.edit().putString("lesson", d).apply(); words(LESSONS[(Date().time / 86400000 % LESSONS.size).toInt()], 9000)
        }
    }
    private fun tick() {
        h.postDelayed({
            val cal = Calendar.getInstance(); val hr = cal.get(Calendar.HOUR_OF_DAY)
            val day = cal.get(Calendar.DAY_OF_YEAR)
            if (!sleeping && !busy) {
                for (slot in listOf(11, 16, 21)) if (hr == slot && prefs.getInt("hs$slot", -1) != day) { prefs.edit().putInt("hs$slot", day).apply(); hashira(); return@postDelayed tick2() }
                if (hr in 8..22 && Random().nextInt(180) == 0) words(NUDGES.random())
            }
            tick()
        }, 60_000)
    }
    private fun tick2() = tick()

    private fun overlay(v: View, w: Int, hgt: Int, x: Int, y: Int): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(w, hgt, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.END; p.x = x; p.y = y; wm.addView(v, p); return p
    }
    private fun words(t: String, ms: Long = 5500) {
        if (sleeping || busy) return
        val tv = TextView(this).apply {
            text = t; setTextColor(0xFFE8ECF8.toInt()); textSize = 12f; maxWidth = dp(220); setPadding(dp(12), dp(8), dp(12), dp(8)); alpha = 0f
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(0xCC0B1020.toInt()); setStroke(1, 0x553A7BFF) }
        }
        overlay(tv, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, small + dp(10), cy - dp(18))
        tv.animate().alpha(1f).translationX(0f).setDuration(500).start()
        h.postDelayed({ tv.animate().alpha(0f).setDuration(600).withEndAction { try { wm.removeView(tv) } catch (_: Exception) {} }.start() }, ms)
    }
    /** Put your pictures in app/src/main/assets/hashira/ (jpg/png). Shows 3 times a day. */
    private fun hashira() {
        val f = assets.list("hashira")?.filter { it.matches(Regex(".*\\.(jpg|jpeg|png|webp)", RegexOption.IGNORE_CASE)) } ?: return
        if (f.isEmpty()) return
        val bmp = assets.open("hashira/" + f.random()).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
        val iv = ImageView(this).apply {
            setImageBitmap(bmp); scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; alpha = 0f
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, dp(18).toFloat()) } }
            scaleX = .5f; scaleY = .5f; rotation = -6f; translationX = dp(80).toFloat()
        }
        overlay(iv, dp(190), dp(260), small + dp(12), (cy - dp(130)).coerceAtLeast(dp(40)))
        iv.animate().alpha(1f).scaleX(1f).scaleY(1f).rotation(0f).translationX(0f).setDuration(700).setInterpolator(OvershootInterpolator(1.2f)).start()
        h.postDelayed({ iv.animate().alpha(0f).scaleX(1.15f).scaleY(1.15f).setDuration(900).withEndAction { try { wm.removeView(iv) } catch (_: Exception) {} }.start() }, 6500)
    }
    override fun onDestroy() { tts?.shutdown(); sr?.destroy(); try { wm.removeView(orb) } catch (_: Exception) {}; super.onDestroy() }
}
