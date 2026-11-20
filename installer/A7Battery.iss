; A7Battery 安装脚本 (Inno Setup 6)
; 生成带卸载注册表项、开始菜单/桌面快捷方式、可选开机自启的 Windows 安装包

#define MyAppName "A7 电量助手"
#define MyAppVersion "1.5.0"
#define MyAppPublisher "ackd7"
#define MyAppURL "https://github.com/ackd7/A7Battery"
#define MyAppExeName "A7Battery.exe"
#define MyAppId "{8F3C2A1E-6D4B-4E7A-9C15-A7B4TTERY120}"

[Setup]
AppId={{#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}/releases/latest
DefaultDirName={autopf}\A7Battery
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
LicenseFile=
OutputDir=..\dist\installer
OutputBaseFilename=A7Battery-Setup-v{#MyAppVersion}
SetupIconFile=..\src\A7Battery\Assets\app.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; 卸载信息写入 HKLM（需管理员），也可改为 HKCU 免管理员
PrivilegesRequired=admin
UninstallDisplayName={#MyAppName} {#MyAppVersion}
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoDescription={#MyAppName} Installer
VersionInfoCopyright=MIT License

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "其他:"; Flags: unchecked
Name: "autostart"; Description: "开机自动启动（托盘运行）"; GroupDescription: "其他:"; Flags: unchecked

[Files]
; 自包含发布输出（464 个文件），整目录复制
Source: "..\src\A7Battery\bin\x64\Release\net8.0-windows\win-x64\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

; 开机自启：写入与程序内设置一致的 HKCU Run 值
[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "A7Battery"; ValueData: """{app}\{#MyAppExeName}"" --minimized"; Flags: uninsdeletevalue; Tasks: autostart

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "立即运行 {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallRun]
; 卸载时先结束正在运行的程序，避免文件占用
Filename: "taskkill"; Parameters: "/F /IM {#MyAppExeName}"; Flags: runhidden skipifdoesntexist; RunOnceId: "A7BatteryStop"

