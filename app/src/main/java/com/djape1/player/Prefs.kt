package com.djape1.player

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("djape1", Context.MODE_PRIVATE)

    fun invite(c: Context): Invite? {
        val p = sp(c)
        val base = p.getString("base", null) ?: return null
        val token = p.getString("token", null) ?: return null
        return Invite(base, token)
    }

    fun saveInvite(c: Context, inv: Invite) {
        sp(c).edit().putString("base", inv.base).putString("token", inv.token).apply()
    }

    fun dark(c: Context): Boolean = sp(c).getBoolean("dark", true)
    fun setDark(c: Context, v: Boolean) { sp(c).edit().putBoolean("dark", v).apply() }

    fun mood(c: Context): String = sp(c).getString("mood", "auto") ?: "auto"
    fun setMood(c: Context, v: String) { sp(c).edit().putString("mood", v).apply() }

    /** Ποιότητα streaming: high (προεπιλογή), medium, low. */
    fun quality(c: Context): String = sp(c).getString("quality", "high") ?: "high"
    fun setQuality(c: Context, v: String) { sp(c).edit().putString("quality", v).apply() }

    /** Τοπικό ιστορικό κομματιών (JSON), νεότερο πρώτο. */
    fun historyJson(c: Context): String = sp(c).getString("history", "[]") ?: "[]"
    fun setHistoryJson(c: Context, v: String) { sp(c).edit().putString("history", v).apply() }
}
