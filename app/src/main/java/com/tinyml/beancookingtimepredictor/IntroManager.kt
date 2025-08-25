package com.tinyml.beancookingtimepredictor

import android.content.Context
import android.content.SharedPreferences

class IntroManager (context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val SHOW_INTRO = "show_intro"
    }

    fun shouldShowIntro(): Boolean {
        return prefs.getBoolean(SHOW_INTRO, true)
    }

    fun setShowIntro(shouldShow: Boolean) {
        prefs.edit().putBoolean(SHOW_INTRO, shouldShow).apply()
    }
}