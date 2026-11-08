using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using System.Text;

namespace MajestyMonitor.Core
{
    /// <summary>User settings, stored in %AppData%\MAJESTY\AirCoolerMonitor\settings.ini.</summary>
    public sealed class AppSettings
    {
        public string Language { get; set; } = "ar";
        public bool Fahrenheit { get; set; }
        public bool AutoStart { get; set; } = true;
        public bool StartMinimized { get; set; }
        public bool CloseToTray { get; set; } = true;
        public bool SendToDevice { get; set; } = true;
        public int RefreshMs { get; set; } = 1000;
        public Dictionary<MetricKind, string> Bindings { get; } = new Dictionary<MetricKind, string>();

        public static string Folder =>
            Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MAJESTY", "AirCoolerMonitor");

        public static string FilePath => Path.Combine(Folder, "settings.ini");

        public bool IsFirstRun { get; private set; }

        /// <summary>When set (demo / preview runs), Save() does nothing.</summary>
        public bool ReadOnly { get; set; }

        public static AppSettings Load()
        {
            var s = new AppSettings();
            try
            {
                if (!File.Exists(FilePath))
                {
                    s.IsFirstRun = true;
                    return s;
                }
                foreach (var raw in File.ReadAllLines(FilePath, Encoding.UTF8))
                {
                    var line = raw.Trim();
                    int eq = line.IndexOf('=');
                    if (eq <= 0 || line.StartsWith("#") || line.StartsWith(";") || line.StartsWith("["))
                        continue;
                    string key = line.Substring(0, eq).Trim();
                    string val = line.Substring(eq + 1).Trim();
                    switch (key)
                    {
                        case "Language": s.Language = val == "en" ? "en" : "ar"; break;
                        case "Fahrenheit": s.Fahrenheit = val == "1"; break;
                        case "AutoStart": s.AutoStart = val == "1"; break;
                        case "StartMinimized": s.StartMinimized = val == "1"; break;
                        case "CloseToTray": s.CloseToTray = val == "1"; break;
                        case "SendToDevice": s.SendToDevice = val == "1"; break;
                        case "RefreshMs":
                            if (int.TryParse(val, NumberStyles.Integer, CultureInfo.InvariantCulture, out int ms))
                                s.RefreshMs = Math.Max(250, Math.Min(5000, ms));
                            break;
                        default:
                            if (key.StartsWith("Sensor.") && Enum.TryParse(key.Substring(7), out MetricKind kind))
                                s.Bindings[kind] = val;
                            break;
                    }
                }
            }
            catch
            {
                // Corrupt file: fall back to defaults.
            }
            return s;
        }

        public void Save()
        {
            if (ReadOnly)
                return;
            try
            {
                Directory.CreateDirectory(Folder);
                var sb = new StringBuilder();
                sb.AppendLine("[General]");
                sb.AppendLine("Language=" + Language);
                sb.AppendLine("Fahrenheit=" + (Fahrenheit ? "1" : "0"));
                sb.AppendLine("AutoStart=" + (AutoStart ? "1" : "0"));
                sb.AppendLine("StartMinimized=" + (StartMinimized ? "1" : "0"));
                sb.AppendLine("CloseToTray=" + (CloseToTray ? "1" : "0"));
                sb.AppendLine("SendToDevice=" + (SendToDevice ? "1" : "0"));
                sb.AppendLine("RefreshMs=" + RefreshMs.ToString(CultureInfo.InvariantCulture));
                sb.AppendLine("[Sensors]");
                foreach (var kv in Bindings.Where(b => !string.IsNullOrEmpty(b.Value)))
                    sb.AppendLine("Sensor." + kv.Key + "=" + kv.Value);
                File.WriteAllText(FilePath, sb.ToString(), Encoding.UTF8);
                IsFirstRun = false;
            }
            catch
            {
                // Ignore: settings are a convenience.
            }
        }

        public AppSettings Clone()
        {
            var copy = new AppSettings
            {
                Language = Language, Fahrenheit = Fahrenheit, AutoStart = AutoStart, StartMinimized = StartMinimized,
                CloseToTray = CloseToTray, SendToDevice = SendToDevice, RefreshMs = RefreshMs,
            };
            foreach (var kv in Bindings)
                copy.Bindings[kv.Key] = kv.Value;
            return copy;
        }
    }
}
