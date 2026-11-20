package top.ackd7.a7battery.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新信息（对应原 C# UpdateInfo）。
 */
data class UpdateInfo(
    var available: Boolean = false,
    var latestVersion: String = "",
    var htmlUrl: String = "",
    var body: String = "",
    var published: String = "",
    var error: String = "",
)

/**
 * GitHub Releases 更新检查（对应原 C# UpdateChecker）。
 * 失败一律静默（返回 Error），不影响主程序启动与运行。
 */
class UpdateChecker {

    fun check(currentVersion: String): UpdateInfo {
        val result = UpdateInfo()
        return try {
            val conn = URL(ApiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "A7Battery-Updater")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val text = conn.inputStream.bufferedReader().use { it.readText() }

            val root = Json.parseToJsonElement(text).jsonObject
            result.latestVersion = root["tag_name"]?.jsonPrimitive?.content ?: ""
            result.htmlUrl = root["html_url"]?.jsonPrimitive?.content ?: LatestUrl
            result.body = root["body"]?.jsonPrimitive?.content ?: ""
            result.published = root["published_at"]?.jsonPrimitive?.content ?: ""
            result.available = isNewer(result.latestVersion, currentVersion)
            result
        } catch (ex: Exception) {
            result.error = ex.message ?: "检查失败"
            result
        }
    }

    companion object {
        const val Repo = "ackd7/A7Battery"
        private const val ApiUrl = "https://api.github.com/repos/$Repo/releases/latest"
        private const val LatestUrl = "https://github.com/$Repo/releases/latest"

        /** 比较 v1.2.3 形式的版本号：若 latest 比 current 新则返回 true。 */
        fun isNewer(latest: String, current: String): Boolean {
            val a = parse(latest)
            val b = parse(current)
            for (i in 0 until 3) {
                if (a[i] != b[i]) return a[i] > b[i]
            }
            return false
        }

        private fun parse(version: String): IntArray {
            val result = intArrayOf(0, 0, 0)
            if (version.isBlank()) return result
            var s = version.trim().trimStart('v', 'V')
            val cut = s.indexOfAny(charArrayOf('-', '+'))
            if (cut >= 0) s = s.substring(0, cut)
            val parts = s.split('.')
            for (i in 0 until minOf(3, parts.size)) {
                result[i] = parts[i].toIntOrNull() ?: 0
            }
            return result
        }
    }
}
