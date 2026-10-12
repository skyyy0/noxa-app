package com.example.ui.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object FormatUtils {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatViews(viewCountStr: String?): String {
        val count = viewCountStr?.toLongOrNull() ?: return ""
        return when {
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM views", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK views", count / 1_000.0)
            else -> "$count views"
        }
    }
}
