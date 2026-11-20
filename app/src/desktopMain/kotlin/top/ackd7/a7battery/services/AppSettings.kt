package top.ackd7.a7battery.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 应用设置（对应原 C# AppSettings），JSON 持久化到 LocalAppData。
 */
@Serializable
data class AppSettings(
    var refreshSeconds: Int = 30,
    var autoStart: Boolean = false,
    var startMinimized: Boolean = false,
    var showPercentInTray: Boolean = true,
    var checkUpdates: Boolean = true,
    var lastUpdateCheck: String = "",
    var skippedVersion: String = "",
    var dpiStages: IntArray = intArrayOf(400, 800, 1600, 3200, 6400, 26000),
    var activeDpiIndex: Int = 2,
    var rateHz: Int = 1000,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        private fun settingsPath(): File {
            val base = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            val dir = File(base, "A7Battery")
            dir.mkdirs()
            return File(dir, "settings.json")
        }

        fun load(): AppSettings {
            return try {
                val f = settingsPath()
                if (f.exists()) {
                    val s = json.decodeFromString<AppSettings>(f.readText()).normalize()
                    s
                } else AppSettings()
            } catch (_: Exception) {
                AppSettings()
            }
        }
    }

    private fun normalize(): AppSettings {
        if (dpiStages.size != 6) dpiStages = intArrayOf(400, 800, 1600, 3200, 6400, 26000)
        for (i in dpiStages.indices) {
            if (dpiStages[i] !in 100..42000) dpiStages[i] = 800
        }
        activeDpiIndex = activeDpiIndex.coerceIn(0, 5)
        if (rateHz < 125) rateHz = 1000
        return this
    }

    fun save() {
        try {
            settingsPath().writeText(json.encodeToString(this))
        } catch (_: Exception) {
        }
    }
}
