using System;
using A7Battery.Services;

class Program
{
    static void Main()
    {
        Console.WriteLine("=== A7 V2 Ultra+ 电量读取验证 ===\n");
        var reader = new MchoseBatteryReader();
        int ok = 0;
        for (int i = 0; i < 5; i++)
        {
            var r = reader.Read();
            Console.WriteLine($"[{i + 1}] 成功={r.Success} 电量={r.Percent}% 充电={r.Charging} 模式={r.Mode} 设备={r.DeviceName}");
            Console.WriteLine($"     raw={r.Raw}");
            if (!string.IsNullOrEmpty(r.Error)) Console.WriteLine($"     error={r.Error}");
            if (r.Success) ok++;
            System.Threading.Thread.Sleep(800);
        }
        Console.WriteLine($"\n完成：{ok}/5 次成功");
    }
}
