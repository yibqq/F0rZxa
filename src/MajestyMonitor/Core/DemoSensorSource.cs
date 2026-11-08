using System;
using System.Collections.Generic;

namespace MajestyMonitor.Core
{
    /// <summary>Simulated values, used with --demo (no hardware access needed).</summary>
    public sealed class DemoSensorSource : ISensorSource
    {
        private readonly DateTime _start = DateTime.Now;
        private readonly Random _rnd = new Random(7);

        public string Warning => null;

        public void Open() { }

        public Snapshot Read(IDictionary<MetricKind, string> bindings)
        {
            double t = (DateTime.Now - _start).TotalSeconds;
            return At(t);
        }

        public Snapshot At(double t)
        {
            double n() => (_rnd.NextDouble() - 0.5) * 0.35;
            double wave = Math.Sin(t / 9.0) * 0.5 + 0.5;
            var s = new Snapshot
            {
                CpuName = "AMD Ryzen 7 7800X3D 8-Core Processor",
                GpuName = "NVIDIA GeForce RTX 4070 SUPER",
                RamTotalGb = 32,
            };
            s[MetricKind.CpuTemp] = 52 + 14 * wave + n() * 2;
            s[MetricKind.CpuLoad] = 28 + 40 * wave + n() * 6;
            s[MetricKind.CpuPower] = 48 + 38 * wave + n() * 3;
            s[MetricKind.CpuClock] = 4650 + 300 * wave + n() * 40;
            s[MetricKind.CpuVoltage] = 1.18 + 0.1 * wave;
            s[MetricKind.GpuTemp] = 46 + 16 * Math.Sin(t / 13.0 + 1) * 0.5 + 8 + n() * 1.5;
            s[MetricKind.GpuLoad] = 35 + 45 * (Math.Sin(t / 7.0 + 2) * 0.5 + 0.5) + n() * 5;
            s[MetricKind.GpuPower] = 95 + 110 * (Math.Sin(t / 7.0 + 2) * 0.5 + 0.5) + n() * 4;
            s[MetricKind.GpuClock] = 2520 + 160 * wave + n() * 20;
            s[MetricKind.GpuMemLoad] = 41 + 6 * wave;
            s[MetricKind.Fan1] = 1180 + 520 * wave + n() * 25;
            s[MetricKind.Fan2] = 1020 + 430 * wave + n() * 25;
            s.RamUsedGb = 13.6 + 2.2 * wave;
            s[MetricKind.RamLoad] = s.RamUsedGb / s.RamTotalGb * 100.0;
            return s;
        }

        public IList<SensorOption> GetOptions(MetricKind kind) => new List<SensorOption>
        {
            new SensorOption("demo", "Demo sensor", 0),
        };

        public void Dispose() { }
    }
}
