package com.orbitfiles.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

object Formatters {
    val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    /** Human readable size using powers of 1024 and Brazilian decimal comma ("1,5 MB"). */
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.size - 1)
        val value = bytes / 1024.0.pow(exp)
        val pattern = if (value >= 100) "%.0f %s" else "%.1f %s"
        return String.format(ptBR, pattern, value, units[exp])
    }

    fun count(n: Int, singular: String, plural: String): String = if (n == 1) "1 $singular" else "$n $plural"

    fun percent(part: Long, total: Long): String =
        if (total <= 0) "0%" else String.format(ptBR, "%.0f%%", part * 100.0 / total)

    fun dateTime(millis: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", ptBR).format(Date(millis))

    fun date(millis: Long): String = SimpleDateFormat("dd/MM/yyyy", ptBR).format(Date(millis))

    /** "agora", "há 5 min", "há 3 h", "ontem", "há 4 dias" or an absolute date. */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - millis
        if (diff < 0) return dateTime(millis)
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "agora"
            diff < hour -> "há ${diff / minute} min"
            diff < day -> "há ${diff / hour} h"
            diff < 2 * day -> "ontem"
            diff < 7 * day -> "há ${diff / day} dias"
            else -> date(millis)
        }
    }

    fun duration(millis: Long): String {
        val totalSec = abs(millis) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(ptBR, "%d:%02d:%02d", h, m, s) else String.format(ptBR, "%d:%02d", m, s)
    }

    fun speed(bytesPerSecond: Double): String = size(bytesPerSecond.toLong()) + "/s"
}
