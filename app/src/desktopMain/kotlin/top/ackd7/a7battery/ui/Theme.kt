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
fun AppTheme(content: @Composable () -> Unit) {
    val controller = remember {
        ThemeController(ColorSchemeMode.System)
    }
    MiuixTheme(controller = controller) {
        content()
    }
}
