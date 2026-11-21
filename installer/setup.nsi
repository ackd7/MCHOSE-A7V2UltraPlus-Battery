; MCHOSE A7 V2 Ultra+ Battery 安装脚本（NSIS 3.x，Unicode）
; 构建（在仓库根目录）：
;   makensis /DAPP_VERSION=4.1.4 /DPRODUCT_VERSION=4.1.4 installer/setup.nsi
; 说明：
;   - 默认按用户安装到 %LOCALAPPDATA%\Programs\MCHOSE-A7V2UltraPlus-Battery，无需管理员权限
;   - 安装向导里可以勾选「创建桌面快捷方式」和「开机自动启动」
;   - 写入 HKCU 卸载信息，可在「设置 → 应用」中正常卸载，并主动刷新桌面图标缓存

Unicode true

!include "MUI2.nsh"
!include "FileFunc.nsh"

!ifndef APP_VERSION
  !define APP_VERSION "4.1.4"
!endif
!ifndef PRODUCT_VERSION
  !define PRODUCT_VERSION "4.1.4"
!endif

!define APP_NAME      "MCHOSE A7 V2 Ultra+ Battery"
!define APP_EXE       "MCHOSE-A7V2UltraPlus-Battery.exe"
!define APP_PUBLISHER "ackd7"
!define APP_REGKEY    "Software\Microsoft\Windows\CurrentVersion\Uninstall\MCHOSE-A7V2UltraPlus-Battery"
!define APP_ICON_SRC  "..\app\src\desktopMain\resources\app.ico"
!ifndef SOURCE_DIR
  !define SOURCE_DIR "..\app\build\compose\binaries\main\app\MCHOSE-A7V2UltraPlus-Battery"
!endif
!ifndef OUT_DIR
  !define OUT_DIR "..\dist"
!endif

Name "${APP_NAME}"
OutFile "${OUT_DIR}\MCHOSE-A7V2UltraPlus-Battery-Setup-${APP_VERSION}.exe"
InstallDir "$LOCALAPPDATA\Programs\MCHOSE-A7V2UltraPlus-Battery"
InstallDirRegKey HKCU "Software\MCHOSE-A7V2UltraPlus-Battery" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "${PRODUCT_VERSION}.0"
VIAddVersionKey "ProductName" "${APP_NAME}"
VIAddVersionKey "FileDescription" "${APP_NAME} 安装程序"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "CompanyName" "${APP_PUBLISHER}"

!define MUI_ICON "${APP_ICON_SRC}"
!define MUI_UNICON "${APP_ICON_SRC}"
!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "立即启动 ${APP_NAME}"
!define MUI_FINISHPAGE_LINK "项目主页（GitHub）"
!define MUI_FINISHPAGE_LINK_LOCATION "https://github.com/ackd7/MCHOSE-A7V2UltraPlus-Battery"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "SimpChinese"

; ---------------- 安装 ----------------

Section "主程序（必需）" SecMain
  SectionIn RO

  ; 安装前先结束正在运行的实例（含旧版本进程名），避免文件占用
  nsExec::Exec 'taskkill /F /IM ${APP_EXE}'
  nsExec::Exec 'taskkill /F /IM A7Battery.exe'
  Sleep 300

  SetOutPath "$INSTDIR"
  File /r "${SOURCE_DIR}\*.*"
  ; 随程序一并安装独立图标文件，快捷方式显式引用它可彻底绕过 Windows 旧 exe 图标缓存
  File "/oname=$INSTDIR\app.ico" "${APP_ICON_SRC}"

  WriteRegStr HKCU "Software\MCHOSE-A7V2UltraPlus-Battery" "InstallDir" "$INSTDIR"

  ; 开始菜单（先删后建，确保图标刷新）
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk"
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\app.ico" 0
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk" "$INSTDIR\Uninstall.exe" "" "$INSTDIR\app.ico" 0

  ; 旧版本遗留的快捷方式、注册表项与安装目录，升级时一并清掉
  Delete "$DESKTOP\A7 电量助手.lnk"
  Delete "$SMPROGRAMS\A7 电量助手\A7 电量助手.lnk"
  Delete "$SMPROGRAMS\A7 电量助手\卸载 A7 电量助手.lnk"
  RMDir "$SMPROGRAMS\A7 电量助手"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "A7Battery"
  DeleteRegKey   HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\A7Battery"
  DeleteRegKey   HKCU "Software\A7Battery"
  RMDir /r "$LOCALAPPDATA\Programs\A7Battery"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; 卸载信息（设置 → 应用）
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayName"     "${APP_NAME}"
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayVersion"  "${APP_VERSION}"
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayIcon"     "$INSTDIR\app.ico"
  WriteRegStr   HKCU "${APP_REGKEY}" "Publisher"       "${APP_PUBLISHER}"
  WriteRegStr   HKCU "${APP_REGKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr   HKCU "${APP_REGKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr   HKCU "${APP_REGKEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKCU "${APP_REGKEY}" "EstimatedSize"   "$0"
  WriteRegDWORD HKCU "${APP_REGKEY}" "NoModify"        1
  WriteRegDWORD HKCU "${APP_REGKEY}" "NoRepair"        1

  ; 通知 Windows Shell 刷新图标缓存（SHCNE_ASSOCCHANGED = 0x08000000）
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd

Section "创建桌面快捷方式" SecDesktop
  Delete "$DESKTOP\${APP_NAME}.lnk"
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\app.ico" 0
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd

Section "开机自动启动" SecStartup
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "MCHOSE-A7V2UltraPlus-Battery" \
    '"$INSTDIR\${APP_EXE}" --minimized'
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMain}    "主程序与内置运行时（约 65 MB）。"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop} "在桌面创建「${APP_NAME}」快捷方式。"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStartup} "开机时自动在后台静默启动，托盘常驻。"
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ---------------- 卸载 ----------------

Section "Uninstall"
  nsExec::Exec 'taskkill /F /IM ${APP_EXE}'
  nsExec::Exec 'taskkill /F /IM A7Battery.exe'
  Sleep 300

  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$DESKTOP\A7 电量助手.lnk"
  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "A7Battery"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk"
  RMDir  "$SMPROGRAMS\${APP_NAME}"

  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "MCHOSE-A7V2UltraPlus-Battery"
  DeleteRegKey   HKCU "${APP_REGKEY}"
  DeleteRegKey   HKCU "Software\MCHOSE-A7V2UltraPlus-Battery"

  ; 保留用户配置（%LOCALAPPDATA%\MCHOSE-A7V2UltraPlus-Battery\settings.json），只删程序目录
  RMDir /r "$INSTDIR"
  System::Call 'shell32::SHChangeNotify(i 0x08000000, i 0, p 0, p 0)'
SectionEnd
