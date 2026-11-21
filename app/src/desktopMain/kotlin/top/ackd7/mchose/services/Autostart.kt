package top.ackd7.mchose.services

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * 开机自启（对应原 C# Autostart），写入当前用户 Run 注册表项。
 */
object Autostart {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = top.ackd7.mchose.APP_ID
    /** 旧版本使用的注册表值名，写入/删除时顺带清掉。 */
    private const val LEGACY_NAME = "A7Battery"

    fun isEnabled(): Boolean {
        return try {
            val value = Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            value.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                val exe = ProcessHandle.current().info().command().orElse(null)
                val lower = exe?.lowercase().orEmpty()
                // 防止本地 gradlew run 调试时误把 java.exe / javaw.exe 写入开机自启
                if (!exe.isNullOrBlank() && !lower.endsWith("\\java.exe") && !lower.endsWith("\\javaw.exe")) {
                    Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, "\"$exe\" --minimized"
                    )
                } else if (!exe.isNullOrBlank()) {
                    top.ackd7.mchose.appLog("跳过写入开机自启（当前为开发期 JVM 进程：$exe）")
                }
            } else {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            }
            // 顺带清掉旧版本写入的值，避免重复自启
            runCatching {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, LEGACY_NAME)
            }
        } catch (_: Exception) {
        }
    }
}
