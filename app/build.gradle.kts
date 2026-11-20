import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.serialization.json)

            // miuix —— MIUI/HyperOS 设计语言（配色 token + 组件）
            implementation(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.miuix.icons)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            // miuix-window 仅 desktop 平台提供
            implementation(libs.miuix.window)
        }
    }
}

// 版本号：优先取 -Pversion=xxx，其次取环境变量 APP_VERSION，默认 2.0.0
val appVersion: String = (project.findProperty("version") as String?)
    ?: System.getenv("APP_VERSION")
    ?: "2.0.0"

compose.desktop {
    application {
        mainClass = "top.ackd7.a7battery.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "A7Battery"
            packageVersion = appVersion

            windows {
                menuGroup = "A7 电量助手"
                // 图标：沿用原 Assets/app.ico
                iconFile.set(project.file("src/desktopMain/resources/app.ico"))
                upgradeUuid = "8f3a1e2c-4b5d-4a6b-8c9e-0f1a2b3c4d5e"
                // 程序内无控制台输出
                console = false
            }
        }
    }
}

compose {
    // 桌面运行时使用 Skiko 渲染，默认即可
}
