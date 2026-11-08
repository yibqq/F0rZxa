using System;
using System.Collections.Generic;

namespace MajestyMonitor.Core
{
    /// <summary>Every value the dashboard shows. The first 11 are sent to the cooler display.</summary>
    public enum MetricKind
    {
        CpuTemp,
        CpuLoad,
        CpuPower,
        CpuClock,
        CpuVoltage,
        GpuTemp,
        GpuLoad,
        GpuPower,
        GpuClock,
        Fan1,
        Fan2,
        GpuMemLoad,
        RamLoad,
    }

    public static class MetricInfo
    {
        public static readonly MetricKind[] All = (MetricKind[])Enum.GetValues(typeof(MetricKind));

        /// <summary>Metrics the user can bind to a specific sensor in Settings.</summary>
        public static readonly MetricKind[] Selectable =
        {
            MetricKind.CpuTemp, MetricKind.CpuLoad, MetricKind.CpuPower, MetricKind.CpuClock, MetricKind.CpuVoltage,
            MetricKind.GpuTemp, MetricKind.GpuLoad, MetricKind.GpuPower, MetricKind.GpuClock, MetricKind.GpuMemLoad,
            MetricKind.Fan1, MetricKind.Fan2,
        };

        public static bool IsTemperature(MetricKind k) => k == MetricKind.CpuTemp || k == MetricKind.GpuTemp;
    }

    /// <summary>A point-in-time reading of every metric. Temperatures are always stored in °C.</summary>
    public sealed class Snapshot
    {
        private readonly double?[] _values = new double?[MetricInfo.All.Length];

        public DateTime Time { get; set; } = DateTime.Now;
        public string CpuName { get; set; } = "";
        public string GpuName { get; set; } = "";
        public double RamUsedGb { get; set; }
        public double RamTotalGb { get; set; }
        public Dictionary<MetricKind, string> SourceNames { get; } = new Dictionary<MetricKind, string>();

        public double? this[MetricKind k]
        {
            get => _values[(int)k];
            set => _values[(int)k] = value;
        }

        public double Get(MetricKind k) => _values[(int)k] ?? 0.0;
    }

    /// <summary>A sensor a metric can be bound to.</summary>
    public sealed class SensorOption
    {
        public SensorOption(string id, string name, double? value)
        {
            Id = id;
            Name = name;
            Value = value;
        }

        public string Id { get; }
        public string Name { get; }
        public double? Value { get; }
        public override string ToString() => Name;
    }
}
