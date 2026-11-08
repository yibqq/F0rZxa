using System;
using System.Collections.Generic;
using System.Linq;
using System.Management;
using LibreHardwareMonitor.Hardware;
using Microsoft.Win32;

namespace MajestyMonitor.Core
{
    /// <summary>Reads real sensors through LibreHardwareMonitor.</summary>
    public sealed class HardwareSensorSource : ISensorSource
    {
        private const string VirtualClockAvg = "virtual:cpu-clock-avg";
        private const string VirtualClockMax = "virtual:cpu-clock-max";
        private const string VirtualAcpiTemp = "virtual:acpi-thermal";

        private readonly object _lock = new object();
        private readonly List<Computer> _computers = new List<Computer>();
        private bool _opened;
        private List<ISensor> _sensors = new List<ISensor>();
        private DateTime _lastRescan = DateTime.MinValue;
        private double? _acpiTemp;
        private DateTime _lastAcpiRead = DateTime.MinValue;

        public string Warning { get; private set; }

        public void Open()
        {
            lock (_lock)
            {
                // One Computer per hardware category: if a single group fails to open
                // (driver missing, unsupported chip...), the others still work.
                var groups = new (string Name, Action<Computer> Enable)[]
                {
                    ("CPU", c => c.IsCpuEnabled = true),
                    ("GPU", c => c.IsGpuEnabled = true),
                    ("Memory", c => c.IsMemoryEnabled = true),
                    ("Motherboard", c => c.IsMotherboardEnabled = true),
                    ("Controller", c => c.IsControllerEnabled = true),
                };
                foreach (var grp in groups)
                {
                    var computer = new Computer();
                    grp.Enable(computer);
                    try
                    {
                        computer.Open();
                        _computers.Add(computer);
                    }
                    catch (Exception ex)
                    {
                        Program.Log("Sensor group " + grp.Name + " unavailable: " + ex.Message);
                        try { computer.Close(); } catch { }
                    }
                }
                _opened = true;
                Rescan();
            }
        }

        private IEnumerable<IHardware> AllHardware => _computers.SelectMany(c => c.Hardware);

        private void Rescan()
        {
            var list = new List<ISensor>();
            foreach (var hw in AllHardware)
            {
                hw.Update();
                list.AddRange(hw.Sensors);
                foreach (var sub in hw.SubHardware)
                {
                    sub.Update();
                    list.AddRange(sub.Sensors);
                }
            }
            _sensors = list;
            _lastRescan = DateTime.Now;
        }

        private void UpdateAll()
        {
            foreach (var hw in AllHardware)
            {
                hw.Update();
                foreach (var sub in hw.SubHardware)
                    sub.Update();
            }
            // New sensors can appear (e.g. GPU waking up); rescan once a minute.
            if ((DateTime.Now - _lastRescan).TotalSeconds > 60)
                Rescan();
        }

        public Snapshot Read(IDictionary<MetricKind, string> bindings)
        {
            var snap = new Snapshot();
            lock (_lock)
            {
                if (!_opened)
                    return snap;
                UpdateAll();

                foreach (var kind in MetricInfo.Selectable)
                {
                    bindings.TryGetValue(kind, out var id);
                    var options = GetOptionsUnlocked(kind);
                    var chosen = !string.IsNullOrEmpty(id) ? options.FirstOrDefault(o => o.Id == id) : null;
                    chosen = chosen ?? options.FirstOrDefault(o => o.Value.HasValue);
                    if (chosen != null)
                    {
                        snap[kind] = chosen.Value;
                        snap.SourceNames[kind] = chosen.Name;
                    }
                }

                snap.CpuName = AllHardware.FirstOrDefault(h => h.HardwareType == HardwareType.Cpu)?.Name ?? "";
                snap.GpuName = OrderedGpus().FirstOrDefault()?.Name ?? "";
                Warning = BuildWarning(snap);
            }

            if (MemoryInfo.TryGet(out var load, out var used, out var total))
            {
                snap[MetricKind.RamLoad] = load;
                snap.RamUsedGb = used;
                snap.RamTotalGb = total;
            }
            return snap;
        }

        private string BuildWarning(Snapshot snap)
        {
            if (snap[MetricKind.CpuTemp].HasValue && snap[MetricKind.CpuPower].HasValue)
                return null;
            if (!IsPawnIoInstalled())
                return "pawnio";
            return null;
        }

        public static bool IsPawnIoInstalled()
        {
            try
            {
                using (var k = Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PawnIO"))
                    return k != null;
            }
            catch
            {
                return false;
            }
        }

        public IList<SensorOption> GetOptions(MetricKind kind)
        {
            lock (_lock)
                return !_opened ? new List<SensorOption>() : GetOptionsUnlocked(kind);
        }

        private IEnumerable<IHardware> OrderedGpus()
        {
            // Prefer the discrete card over the integrated one.
            return AllHardware
                .Where(h => h.HardwareType == HardwareType.GpuNvidia || h.HardwareType == HardwareType.GpuAmd || h.HardwareType == HardwareType.GpuIntel)
                .OrderBy(h => h.HardwareType == HardwareType.GpuIntel ? 1 : 0);
        }

        private static bool IsCpu(ISensor s) => s.Hardware.HardwareType == HardwareType.Cpu;

        private static bool IsGpu(ISensor s) =>
            s.Hardware.HardwareType == HardwareType.GpuNvidia ||
            s.Hardware.HardwareType == HardwareType.GpuAmd ||
            s.Hardware.HardwareType == HardwareType.GpuIntel;

        private static int GpuRank(ISensor s) => s.Hardware.HardwareType == HardwareType.GpuIntel ? 1 : 0;

        private static int NameRank(ISensor s, params string[] preferred)
        {
            for (int i = 0; i < preferred.Length; i++)
                if (string.Equals(s.Name, preferred[i], StringComparison.OrdinalIgnoreCase))
                    return i;
            for (int i = 0; i < preferred.Length; i++)
                if (s.Name.IndexOf(preferred[i], StringComparison.OrdinalIgnoreCase) >= 0)
                    return preferred.Length + i;
            return 100;
        }

        private static SensorOption ToOption(ISensor s, bool withHardware = true)
        {
            string name = withHardware ? $"{s.Name}  ·  {s.Hardware.Name}" : s.Name;
            double? value = s.Value.HasValue && !float.IsNaN(s.Value.Value) ? s.Value.Value : (double?)null;
            return new SensorOption(s.Identifier.ToString(), name, value);
        }

        private List<SensorOption> Pick(Func<ISensor, bool> filter, Func<ISensor, int> rank)
        {
            return _sensors.Where(filter)
                .OrderBy(s => s.Value.HasValue ? 0 : 1)
                .ThenBy(rank)
                .Select(s => ToOption(s))
                .ToList();
        }

        private List<SensorOption> GetOptionsUnlocked(MetricKind kind)
        {
            switch (kind)
            {
                case MetricKind.CpuTemp:
                {
                    var list = Pick(s => IsCpu(s) && s.SensorType == SensorType.Temperature && s.Name.IndexOf("Distance", StringComparison.OrdinalIgnoreCase) < 0,
                        s => NameRank(s, "Core (Tctl/Tdie)", "CPU Package", "Core (Tctl)", "Core Average", "Core Max", "Tdie"));
                    var acpi = ReadAcpiTemperature();
                    if (acpi.HasValue)
                        list.Add(new SensorOption(VirtualAcpiTemp, "ACPI Thermal Zone", acpi));
                    return list;
                }
                case MetricKind.CpuLoad:
                    return Pick(s => IsCpu(s) && s.SensorType == SensorType.Load, s => NameRank(s, "CPU Total", "Total"));
                case MetricKind.CpuPower:
                    return Pick(s => IsCpu(s) && s.SensorType == SensorType.Power, s => NameRank(s, "CPU Package", "Package", "Cores"));
                case MetricKind.CpuClock:
                {
                    var cores = _sensors.Where(s => IsCpu(s) && s.SensorType == SensorType.Clock && s.Name.StartsWith("Core", StringComparison.OrdinalIgnoreCase)
                                                    && s.Value.HasValue && s.Value.Value > 0).ToList();
                    var list = new List<SensorOption>();
                    if (cores.Count > 0)
                    {
                        list.Add(new SensorOption(VirtualClockAvg, "Average core clock", cores.Average(c => c.Value.Value)));
                        list.Add(new SensorOption(VirtualClockMax, "Highest core clock", cores.Max(c => c.Value.Value)));
                    }
                    list.AddRange(Pick(s => IsCpu(s) && s.SensorType == SensorType.Clock && s.Name.IndexOf("Bus", StringComparison.OrdinalIgnoreCase) < 0,
                        s => NameRank(s, "Core #1")));
                    return list;
                }
                case MetricKind.CpuVoltage:
                    return Pick(s => s.SensorType == SensorType.Voltage &&
                                     (IsCpu(s) || s.Name.IndexOf("Vcore", StringComparison.OrdinalIgnoreCase) >= 0 || s.Name.IndexOf("CPU", StringComparison.OrdinalIgnoreCase) >= 0),
                        s => NameRank(s, "Core (SVI2 TFN)", "CPU Core", "Vcore", "Core (SVI3 TFN)", "Core", "CPU Core #1"));
                case MetricKind.GpuTemp:
                    return Pick(s => IsGpu(s) && s.SensorType == SensorType.Temperature, s => GpuRank(s) * 1000 + NameRank(s, "GPU Core", "GPU Hot Spot"));
                case MetricKind.GpuLoad:
                    return Pick(s => IsGpu(s) && s.SensorType == SensorType.Load, s => GpuRank(s) * 1000 + NameRank(s, "GPU Core", "D3D 3D"));
                case MetricKind.GpuPower:
                    return Pick(s => IsGpu(s) && s.SensorType == SensorType.Power, s => GpuRank(s) * 1000 + NameRank(s, "GPU Package", "GPU Power", "GPU Core", "GPU Total"));
                case MetricKind.GpuClock:
                    return Pick(s => IsGpu(s) && s.SensorType == SensorType.Clock, s => GpuRank(s) * 1000 + NameRank(s, "GPU Core", "GPU Shader"));
                case MetricKind.GpuMemLoad:
                    return Pick(s => IsGpu(s) && s.SensorType == SensorType.Load && s.Name.IndexOf("Memory", StringComparison.OrdinalIgnoreCase) >= 0,
                        s => GpuRank(s) * 1000 + NameRank(s, "GPU Memory", "D3D Dedicated Memory"));
                case MetricKind.Fan1:
                    return Pick(s => s.SensorType == SensorType.Fan,
                        s => (s.Value.GetValueOrDefault() > 0 ? 0 : 500) + NameRank(s, "CPU Fan", "CPU", "Fan #1", "Fan #2"));
                case MetricKind.Fan2:
                {
                    var list = Pick(s => s.SensorType == SensorType.Fan,
                        s => (s.Value.GetValueOrDefault() > 0 ? 0 : 500) + NameRank(s, "Pump", "AIO", "CPU Optional", "CPU OPT", "System Fan", "Fan #2", "Fan #3"));
                    // Don't default fan 2 to the same sensor as fan 1 if there is another one.
                    var fan1 = GetOptionsUnlocked(MetricKind.Fan1).FirstOrDefault(o => o.Value.HasValue);
                    if (fan1 != null && list.Count > 1 && list[0].Id == fan1.Id)
                    {
                        var first = list[0];
                        list.RemoveAt(0);
                        list.Insert(Math.Min(1, list.Count), first);
                    }
                    return list;
                }
                default:
                    return new List<SensorOption>();
            }
        }

        private double? ReadAcpiTemperature()
        {
            if ((DateTime.Now - _lastAcpiRead).TotalSeconds < 5)
                return _acpiTemp;
            _lastAcpiRead = DateTime.Now;
            try
            {
                using (var searcher = new ManagementObjectSearcher(@"root\WMI", "SELECT CurrentTemperature FROM MSAcpi_ThermalZoneTemperature"))
                {
                    foreach (ManagementObject mo in searcher.Get())
                    {
                        double kelvinTenths = Convert.ToDouble(mo["CurrentTemperature"]);
                        double c = kelvinTenths / 10.0 - 273.15;
                        if (c > 0 && c < 125)
                            return _acpiTemp = Math.Round(c, 1);
                    }
                }
            }
            catch
            {
                // Not available on this machine.
            }
            return _acpiTemp = null;
        }

        public void Dispose()
        {
            lock (_lock)
            {
                foreach (var c in _computers)
                    try { c.Close(); } catch { }
                _computers.Clear();
                _opened = false;
            }
        }
    }
}
