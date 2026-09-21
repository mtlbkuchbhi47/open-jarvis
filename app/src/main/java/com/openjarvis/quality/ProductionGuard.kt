package com.openjarvis.quality

/** Central safety invariants used by high-impact Jarvis features. */
object ProductionGuard {
    fun safeSitePath(path: String): String {
        val normalized = path.replace('\\', '/').removePrefix("/")
        require(normalized.isNotBlank() && !normalized.split('/').contains("..")) { "Unsafe path" }
        return normalized
    }
    fun requireHttps(url: String) {
        require(url.startsWith("https://", ignoreCase = true)) { "HTTPS is required" }
    }
    fun boundedInt(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)
}
