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
                    return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(SettingsPath)) ?? new AppSettings();
            }
            catch { }
            return new AppSettings();
        }

        public void Save()
        {
            try { File.WriteAllText(SettingsPath, JsonSerializer.Serialize(this)); }
            catch { }
        }
    }
}
