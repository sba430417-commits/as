package com.example.passvault.util

import android.content.Context

object CategoryManager {
    private const val PREFS = "categories"
    private const val KEY = "names"
    private const val ALL = "الكل"
    private const val DEFAULT = "الرئيسي"

    fun get(context: Context): List<String> {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, setOf(DEFAULT))?.toList().orEmpty()
        return listOf(ALL) + (saved + DEFAULT).distinct().sorted()
    }

    fun add(context: Context, name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank() || clean == ALL) return false
        val names = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (!names.add(clean)) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, names).apply()
        return true
    }
}
