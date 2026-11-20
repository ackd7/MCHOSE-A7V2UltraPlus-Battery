using System;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;

namespace A7Battery.Services
{
    public sealed class UpdateInfo
    {
        public bool Available;
        public string LatestVersion = "";
        public string HtmlUrl = "";
        public string Body = "";
        public string Published = "";
        public string Error = "";
    }

    /// <summary>
    /// GitHub Releases 更新检查。后台请求 releases/latest，比较语义化版本号。
    /// 失败一律静默（返回 Error），不影响主程序启动与运行。
    /// </summary>
    public sealed class UpdateChecker
    {
        public const string Repo = "ackd7/A7Battery";
        private const string ApiUrl = "https://api.github.com/repos/" + Repo + "/releases/latest";
        private const string LatestUrl = "https://github.com/" + Repo + "/releases/latest";

        private static readonly HttpClient Client = CreateClient();

        private static HttpClient CreateClient()
        {
            var c = new HttpClient();
            c.Timeout = TimeSpan.FromSeconds(8);
            c.DefaultRequestHeaders.Add("User-Agent", "A7Battery-Updater");
            c.DefaultRequestHeaders.Add("Accept", "application/vnd.github+json");
            return c;
        }

        public async Task<UpdateInfo> CheckAsync(string currentVersion)
        {
            var result = new UpdateInfo();
            try
            {
                var json = await Client.GetStringAsync(ApiUrl).ConfigureAwait(false);
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;

                string tag = root.TryGetProperty("tag_name", out var t) ? t.GetString() ?? "" : "";
                result.LatestVersion = tag;
                result.HtmlUrl = root.TryGetProperty("html_url", out var h) ? h.GetString() ?? LatestUrl : LatestUrl;
                result.Body = root.TryGetProperty("body", out var b) ? b.GetString() ?? "" : "";
                result.Published = root.TryGetProperty("published_at", out var p) ? p.GetString() ?? "" : "";
                result.Available = IsNewer(tag, currentVersion);
            }
            catch (Exception ex)
            {
                result.Error = ex.Message;
            }
            return result;
        }

        /// <summary>比较 v1.2.3 形式的版本号：若 latest 比 current 新则返回 true。</summary>
        public static bool IsNewer(string latest, string current)
        {
            var a = Parse(latest);
            var b = Parse(current);
            for (int i = 0; i < 3; i++)
                if (a[i] != b[i]) return a[i] > b[i];
            return false;
        }

        private static int[] Parse(string version)
        {
            var result = new int[3];
            if (string.IsNullOrEmpty(version)) return result;
            var s = version.Trim().TrimStart('v', 'V');
            int cut = s.IndexOfAny(new[] { '-', '+' });
            if (cut >= 0) s = s.Substring(0, cut);
            var parts = s.Split('.');
            for (int i = 0; i < 3 && i < parts.Length; i++)
                if (int.TryParse(parts[i], out int n)) result[i] = n;
            return result;
        }
    }
}
