using System;
using System.Windows.Threading;

namespace A7Battery.Services
{
    /// <summary>
    /// 电池轮询：在 WPF UI 线程的 DispatcherTimer 上读取，单次约 150ms，简单可靠。
    /// </summary>
    public sealed class BatteryMonitor
    {
        private readonly MchoseBatteryReader _reader = new();
        private readonly DispatcherTimer _timer;
        private BatteryInfo _current = new();
        private bool _busy;

        public event EventHandler<BatteryInfo>? ReadingChanged;
        public BatteryInfo Current => _current;
        public int IntervalSeconds { get; private set; } = 30;

        public BatteryMonitor()
        {
            _timer = new DispatcherTimer();
            _timer.Tick += (_, _) => Refresh();
        }

        public void Start(int intervalSeconds)
        {
            IntervalSeconds = Math.Clamp(intervalSeconds, 5, 900);
            _timer.Interval = TimeSpan.FromSeconds(IntervalSeconds);
            _timer.Start();
            Refresh();
        }

        public void SetInterval(int seconds)
        {
            IntervalSeconds = Math.Clamp(seconds, 5, 900);
            _timer.Interval = TimeSpan.FromSeconds(IntervalSeconds);
        }

        public void Stop() => _timer.Stop();

        public void Refresh()
        {
            if (_busy) return;
            _busy = true;
            try
            {
                var result = _reader.Read(_current);
                if (result.Success) _current = result;
                else if (_current.Success) { _current.UpdatedAt = result.UpdatedAt; _current.Error = result.Error; }
                else _current = result;
                ReadingChanged?.Invoke(this, _current);
            }
            catch (Exception ex)
            {
                var r = new BatteryInfo { Error = ex.Message, UpdatedAt = DateTime.Now };
                _current = r;
                ReadingChanged?.Invoke(this, r);
            }
            finally { _busy = false; }
        }
    }
}
