package com.sesos.app

object UrlUtils {
    private val regex = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)

    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val m = regex.find(text) ?: return null
        return m.value.trimEnd('.', ',', ')', ']', '"', '\'', '،')
    }

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").trim().take(80).ifBlank { "sesos" }
}
