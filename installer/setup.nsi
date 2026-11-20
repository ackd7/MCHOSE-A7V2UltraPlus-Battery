; A7 电量助手 安装脚本（NSIS 3.x，Unicode）
; 构建（在仓库根目录）：
;   makensis /DAPP_VERSION=2.0.5 installer/setup.nsi
; 说明：
;   - 默认按用户安装到 %LOCALAPPDATA%\Programs\A7Battery，无需管理员权限
;   - 安装向导里可以勾选「创建桌面快捷方式」和「开机自动启动」
;   - 写入 HKCU 卸载信息，可在「设置 → 应用」中正常卸载

Unicode true

!include "MUI2.nsh"
!include "FileFunc.nsh"

!ifndef APP_VERSION
  !define APP_VERSION "0.0.0"
!endif

!define APP_NAME      "A7 电量助手"
!define APP_EXE       "A7Battery.exe"
!define APP_PUBLISHER "ackd7"
!define APP_REGKEY    "Software\Microsoft\Windows\CurrentVersion\Uninstall\A7Battery"
!ifndef SOURCE_DIR
  !define SOURCE_DIR "..\app\build\compose\binaries\main\app\A7Battery"
!endif
!ifndef OUT_DIR
  !define OUT_DIR "..\dist"
!endif

Name "${APP_NAME}"
OutFile "${OUT_DIR}\A7Battery-Setup-${APP_VERSION}.exe"
InstallDir "$LOCALAPPDATA\Programs\A7Battery"
InstallDirRegKey HKCU "Software\A7Battery" "InstallDir"
RequestExecutionLevel user
SetCompressor /SOLID lzma
ShowInstDetails show
ShowUninstDetails show

VIProductVersion "1.0.0.0"
VIAddVersionKey "ProductName" "${APP_NAME}"
VIAddVersionKey "FileDescription" "${APP_NAME} 安装程序"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "CompanyName" "${APP_PUBLISHER}"

!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "立即启动 ${APP_NAME}"
!define MUI_FINISHPAGE_LINK "项目主页（GitHub）"
!define MUI_FINISHPAGE_LINK_LOCATION "https://github.com/ackd7/A7Battery"

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

  ; 安装前先结束正在运行的实例，避免文件占用
  nsExec::Exec 'taskkill /F /IM ${APP_EXE}'
  Sleep 300

  SetOutPath "$INSTDIR"
  File /r "${SOURCE_DIR}\*.*"

  WriteRegStr HKCU "Software\A7Battery" "InstallDir" "$INSTDIR"

  ; 开始菜单
  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
  CreateShortCut "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk" "$INSTDIR\Uninstall.exe"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  ; 卸载信息（设置 → 应用）
  ${GetSize} "$INSTDIR" "/S=0K" $0 $1 $2
  IntFmt $0 "0x%08X" $0
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayName"     "${APP_NAME}"
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayVersion"  "${APP_VERSION}"
  WriteRegStr   HKCU "${APP_REGKEY}" "DisplayIcon"     "$INSTDIR\${APP_EXE}"
  WriteRegStr   HKCU "${APP_REGKEY}" "Publisher"       "${APP_PUBLISHER}"
  WriteRegStr   HKCU "${APP_REGKEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr   HKCU "${APP_REGKEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr   HKCU "${APP_REGKEY}" "QuietUninstallString" '"$INSTDIR\Uninstall.exe" /S'
  WriteRegDWORD HKCU "${APP_REGKEY}" "EstimatedSize"   "$0"
  WriteRegDWORD HKCU "${APP_REGKEY}" "NoModify"        1
  WriteRegDWORD HKCU "${APP_REGKEY}" "NoRepair"        1
SectionEnd

Section "创建桌面快捷方式" SecDesktop
  CreateShortCut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
SectionEnd

Section "开机自动启动" SecStartup
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "A7Battery" \
    '"$INSTDIR\${APP_EXE}" --minimized'
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SecMain}    "主程序与内置运行时（约 70 MB）。"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecDesktop} "在桌面创建「${APP_NAME}」快捷方式。"
  !insertmacro MUI_DESCRIPTION_TEXT ${SecStartup} "开机时自动在后台启动，托盘常驻。"
!insertmacro MUI_FUNCTION_DESCRIPTION_END

; ---------------- 卸载 ----------------

Section "Uninstall"
  nsExec::Exec 'taskkill /F /IM ${APP_EXE}'
  Sleep 300

  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\卸载 ${APP_NAME}.lnk"
  RMDir  "$SMPROGRAMS\${APP_NAME}"

  DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "A7Battery"
  DeleteRegKey   HKCU "${APP_REGKEY}"
  DeleteRegKey   HKCU "Software\A7Battery"

  ; 保留用户配置（%LOCALAPPDATA%\A7Battery\settings.json），只删程序目录
  RMDir /r "$INSTDIR"
SectionEnd
