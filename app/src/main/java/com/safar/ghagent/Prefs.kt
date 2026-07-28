package com.safar.ghagent

import android.content.Context

/**
 * Sozlamalar (token'lar, tanlangan model) SharedPreferences'da saqlanadi.
 * Har safar ilova ochilganda oldingi kiritilgan qiymatlar avtomatik yuklanadi.
 */
object Prefs {
    private const val FILE = "ghagent_prefs"
    private const val KEY_GITHUB_TOKEN = "github_token"
    private const val KEY_GEMINI_KEY = "gemini_key"
    private const val KEY_GEMINI_MODEL = "gemini_model"

    val AVAILABLE_MODELS = listOf(
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-pro",
        "gemini-1.5-flash"
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getGithubToken(ctx: Context): String = prefs(ctx).getString(KEY_GITHUB_TOKEN, "") ?: ""
    fun setGithubToken(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_GITHUB_TOKEN, value).apply()

    fun getGeminiKey(ctx: Context): String = prefs(ctx).getString(KEY_GEMINI_KEY, "") ?: ""
    fun setGeminiKey(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_GEMINI_KEY, value).apply()

    fun getGeminiModel(ctx: Context): String = prefs(ctx).getString(KEY_GEMINI_MODEL, AVAILABLE_MODELS[1]) ?: AVAILABLE_MODELS[1]
    fun setGeminiModel(ctx: Context, value: String) = prefs(ctx).edit().putString(KEY_GEMINI_MODEL, value).apply()

    fun isConfigured(ctx: Context): Boolean =
        getGithubToken(ctx).isNotBlank() && getGeminiKey(ctx).isNotBlank()
}
