import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// 版本号：优先取 -PappVersion=xxx，其次取环境变量 APP_VERSION，默认 4.1.0
// 注意：不能用 -Pversion，因为它与 Gradle 内建的 project.version 属性冲突；
// Windows MSI (jpackage) 要求纯三段数字 X.Y.Z，自动剥离 -beta.1 等预发布后缀。
val rawAppVersion: String = (project.findProperty("appVersion") as String?)
    ?: System.getenv("APP_VERSION")
    ?: "4.1.0"
val appVersion: String = rawAppVersion.substringBefore('-').substringBefore('+')

kotlin {
    jvm("desktop")

    // miuix 用 JDK 21 编译（class file version 65），必须用 21 工具链编译+打包，
    // 否则运行时内嵌 JRE 无法加载 miuix 的类
    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)

            implementation(libs.kotlinx.serialization.json)

            // miuix —— MIUI/HyperOS 设计语言（配色 token + 组件）
            implementation(libs.miuix.ui)
            implementation(libs.miuix.icons)
        }

        val desktopMain = getByName("desktopMain")
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            // 提供 Dispatchers.Main（Swing/EDT）
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "top.ackd7.mchose.MainKt"
        // JNA 需要 native access 权限（JDK 17+）
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8")

        nativeDistributions {
            // MSI 作为备用安装包；主推 dist 里的 NSIS Setup（中文向导，可选桌面快捷方式）
            targetFormats(TargetFormat.Msi)
            packageName = "MCHOSE-A7V2UltraPlus-Battery"
            packageVersion = appVersion
            // 显示名称与版本信息（exe 属性里的"产品名称/公司"）
            description = "MCHOSE A7 V2 Ultra+ Battery"
            vendor = "ackd7"
            copyright = "© 2026 ackd7"

            windows {
                // 注意：menuGroup 不用中文，避免 GitHub Actions Windows runner 编码乱码导致打包失败
                menuGroup = "MCHOSE A7 V2 Ultra+ Battery"
                // MSI 安装时创建开始菜单与桌面快捷方式
                menu = true
                shortcut = true
                // 图标：沿用原 Assets/app.ico
                iconFile.set(project.file("src/desktopMain/resources/app.ico"))
                upgradeUuid = "8f3a1e2c-4b5d-4a6b-8c9e-0f1a2b3c4d5e"
                // 程序内无控制台输出
                console = false
            }
        }
    }
}

// 开发用：本地 HID 诊断（不参与发布打包）
tasks.register<JavaExec>("diag") {
    group = "verification"
    description = "本地 HID 诊断：枚举设备并打印读取细节"
    val desktopMain = kotlin.targets.getByName("desktop").compilations.getByName("main")
    dependsOn(tasks.named("desktopMainClasses"))
    mainClass.set("top.ackd7.mchose.DiagKt")
    classpath = files(desktopMain.output.allOutputs, desktopMain.runtimeDependencyFiles)
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dfile.encoding=UTF-8")
}
