using System;
using System.IO;
using System.Text.Json;

namespace A7Battery.Services
{
    public sealed class AppSettings
    {
        public int RefreshSeconds { get; set; } = 30;
        public bool AutoStart { get; set; } = false;
        public bool StartMinimized { get; set; } = false;
        public bool ShowPercentInTray { get; set; } = true;

        // 主题取色
        public ThemeMode ThemeMode { get; set; } = ThemeMode.Static;
        public string StaticSeedColor { get; set; } = "#6750A4";
        public PaletteStyle PaletteStyle { get; set; } = PaletteStyle.TonalSpot;

        // 自动更新检查
        public bool CheckUpdates { get; set; } = true;
        public DateTime LastUpdateCheck { get; set; } = default;
        public string SkippedVersion { get; set; } = "";

        // 鼠标 DPI / 回报率（本地记忆，真实值以设备为准）
        public int[] DpiStages { get; set; } = { 400, 800, 1600, 3200, 6400, 26000 };
        public int ActiveDpiIndex { get; set; } = 2;
        public int RateHz { get; set; } = 1000;

        private static string SettingsPath
        {
            get
            {
                var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "A7Battery");
                Directory.CreateDirectory(dir);
                return Path.Combine(dir, "settings.json");
            }
        }

        public static AppSettings Load()
        {
            try
            {
                if (File.Exists(SettingsPath))
                {
                    var s = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(SettingsPath)) ?? new AppSettings();
                    s.Normalize();
                    return s;
                }
            }
            catch { }
            return new AppSettings();
        }

        private void Normalize()
        {
            if (DpiStages == null || DpiStages.Length != 6)
                DpiStages = new[] { 400, 800, 1600, 3200, 6400, 26000 };
            for (int i = 0; i < 6; i++)
                if (DpiStages[i] < 100 || DpiStages[i] > 30000) DpiStages[i] = 800;
            ActiveDpiIndex = Math.Clamp(ActiveDpiIndex, 0, 5);
            if (RateHz < 125) RateHz = 1000;
        }

        public void Save()
        {
            try { File.WriteAllText(SettingsPath, JsonSerializer.Serialize(this)); }
            catch { }
        }
    }
}
