import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// 版本号：优先取 -PappVersion=xxx，其次取环境变量 APP_VERSION，默认 2.0.0
// 注意：不能用 -Pversion，因为它与 Gradle 内建的 project.version 属性冲突
val appVersion: String = (project.findProperty("appVersion") as String?)
    ?: System.getenv("APP_VERSION")
    ?: "2.0.0"

kotlin {
    jvm("desktop")

    // miuix 用 JDK 21 编译（class file version 65），必须用 21 工具链编译+打包，
    // 否则运行时内嵌 JRE 无法加载 miuix 的类
    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

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
        }
    }
}

compose.desktop {
    application {
        mainClass = "top.ackd7.a7battery.MainKt"

        // JNA 需要 native access 权限（JDK 17+）
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "A7Battery"
            packageVersion = appVersion

            windows {
                // 注意：menuGroup 不用中文，避免 GitHub Actions Windows runner 编码乱码导致打包失败
                menuGroup = "A7Battery"
                // 图标：沿用原 Assets/app.ico
                iconFile.set(project.file("src/desktopMain/resources/app.ico"))
                upgradeUuid = "8f3a1e2c-4b5d-4a6b-8c9e-0f1a2b3c4d5e"
                // 程序内无控制台输出
                console = false
            }
        }
    }
}
