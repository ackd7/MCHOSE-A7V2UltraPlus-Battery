package top.ackd7.mchose.services

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * 开机自启（对应原 C# Autostart），写入当前用户 Run 注册表项。
 */
object Autostart {

    private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val APP_NAME = "A7Battery"

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
                if (!exe.isNullOrBlank()) {
                    Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME, "\"$exe\" --minimized"
                    )
                }
            } else {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
            }
        } catch (_: Exception) {
        }
    }
}
