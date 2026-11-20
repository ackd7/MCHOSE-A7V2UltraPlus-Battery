package top.ackd7.a7battery.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用主题：使用 miuix 的 ThemeController，跟随系统浅色/深色模式。
 * 配色 token 即 miuix 的 lightColorScheme() / darkColorScheme() 默认值
 * （主色 #3482FF 等），不再使用 WPF 的 Material Design 3。
 */
@Composable
fun AppTheme(
    mode: ColorSchemeMode = ColorSchemeMode.System,
    content: @Composable () -> Unit,
) {
    // 模式变化时重建 controller，保证浅色/深色即时生效
    val controller = remember(mode) { ThemeController(mode) }
    MiuixTheme(controller = controller) {
        content()
    }
}

/** 把设置里的字符串映射成 miuix 的配色模式。 */
fun themeModeOf(mode: String): ColorSchemeMode = when (mode) {
    "light" -> ColorSchemeMode.Light
    "dark" -> ColorSchemeMode.Dark
    else -> ColorSchemeMode.System
}
