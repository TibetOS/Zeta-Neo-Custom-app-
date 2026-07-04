package com.traffko.outlanderhub.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val tag: String,
    val htmlUrl: String,
    val apkUrl: String?,
)

/**
 * Checks the project's GitHub Releases for something newer than the
 * installed build. Releases are published by .github/workflows/release.yml
 * when a `vX.Y.Z` tag is pushed.
 */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/TibetOS/Zeta-Neo-Custom-app-/releases/latest"

    /** Fetches the latest release, or null on any network/parse failure. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                val json = JSONObject(connection.inputStream.bufferedReader().readText())
                val assets = json.optJSONArray("assets")
                val apkUrl = (0 until (assets?.length() ?: 0))
                    .asSequence()
                    .map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
                ReleaseInfo(
                    tag = json.getString("tag_name"),
                    htmlUrl = json.getString("html_url"),
                    apkUrl = apkUrl,
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /**
     * True when [candidateTag] (e.g. `"v1.2.0"`) is newer than [currentVersion]
     * (e.g. `"1.1.3"` or `"0.1.0-dev"`). Compares dotted numeric parts;
     * pre-release suffixes are ignored. Unparseable input is never "newer".
     */
    fun isNewer(currentVersion: String, candidateTag: String): Boolean {
        val current = parse(currentVersion) ?: return false
        val candidate = parse(candidateTag) ?: return false
        val length = maxOf(current.size, candidate.size)
        for (i in 0 until length) {
            val a = current.getOrElse(i) { 0 }
            val b = candidate.getOrElse(i) { 0 }
            if (b != a) return b > a
        }
        return false
    }

    private fun parse(version: String): List<Int>? = version
        .trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: return null }
        .takeIf { it.isNotEmpty() }
}
