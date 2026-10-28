package com.sutra.app
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val tv = TextView(this).apply {
            text = "Sutra\n\nI float on your screen and listen when you tap me.\nGrant microphone and 'display over other apps', and I start."
            setTextColor(0xFFE8ECF8.toInt()); textSize = 16f; gravity = Gravity.CENTER; setPadding(60, 0, 60, 40)
        }
        val bt = Button(this).apply { text = "Wake Sutra"; setOnClickListener { go() } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFF0B1020.toInt())
            addView(tv); addView(bt)
        })
    }
    override fun onResume() { super.onResume(); if (ready()) go() }
    private fun ready() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == 0 && Settings.canDrawOverlays(this)
    private fun go() {
        val need = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != 0) need += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != 0) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) { requestPermissions(need.toTypedArray(), 1); return }
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        startForegroundService(Intent(this, SutraService::class.java)); finish()
    }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) { go() }
}
