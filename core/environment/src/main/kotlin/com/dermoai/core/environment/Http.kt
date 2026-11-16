package com.dermoai.core.environment

import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal GET with hard timeouts. `URL.readText()` has none, so a stalled
 * connection would hang the Home screen — and the background alert worker —
 * indefinitely.
 */
internal fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "DermoAI/2.0 (Android)")
        check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode} from ${conn.url.host}" }
        return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
        conn.disconnect()
    }
}
