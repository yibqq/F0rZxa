using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using HidSharp;
using HidSharp.Reports;

namespace MajestyMonitor.Core
{
    /// <summary>
    /// Keeps a connection to every MAJESTY cooler plugged in, reconnects automatically
    /// and pushes the latest payload to the display.
    /// </summary>
    public sealed class CoolerLink : IDisposable
    {
        private sealed class Connection
        {
            public HidDevice Device;
            public HidStream Stream;
            public int ReportLength;
            public byte ReportId;
        }

        private readonly object _lock = new object();
        private readonly List<Connection> _connections = new List<Connection>();
        private volatile bool _rescanRequested = true;
        private DateTime _lastScan = DateTime.MinValue;

        public CoolerLink()
        {
            try { DeviceList.Local.Changed += (s, e) => _rescanRequested = true; } catch { }
        }

        public int ConnectedCount
        {
            get { lock (_lock) return _connections.Count; }
        }

        public bool IsConnected => ConnectedCount > 0;

        public event EventHandler ConnectionChanged;

        /// <summary>Opens any newly attached coolers. Cheap to call often.</summary>
        public void EnsureConnected()
        {
            bool due = _rescanRequested || (_connections.Count == 0 && (DateTime.Now - _lastScan).TotalSeconds >= 2);
            if (!due)
                return;
            _rescanRequested = false;
            _lastScan = DateTime.Now;

            bool changed = false;
            lock (_lock)
            {
                IEnumerable<HidDevice> found;
                try
                {
                    found = DeviceList.Local.GetHidDevices(CoolerProtocol.VendorId, CoolerProtocol.ProductId).ToList();
                }
                catch
                {
                    return;
                }

                foreach (var dev in found)
                {
                    if (_connections.Any(c => c.Device.DevicePath == dev.DevicePath))
                        continue;
                    int len;
                    try { len = dev.GetMaxOutputReportLength(); } catch { continue; }
                    if (len <= 1)
                        continue; // collection without an output report

                    if (!dev.TryOpen(out HidStream stream))
                        continue;
                    stream.WriteTimeout = 1000;
                    _connections.Add(new Connection
                    {
                        Device = dev,
                        Stream = stream,
                        ReportLength = len,
                        ReportId = FindOutputReportId(dev),
                    });
                    changed = true;
                }
            }
            if (changed)
                ConnectionChanged?.Invoke(this, EventArgs.Empty);
        }

        private static byte FindOutputReportId(HidDevice dev)
        {
            try
            {
                var report = dev.GetReportDescriptor().OutputReports.FirstOrDefault();
                return report?.ReportID ?? 0;
            }
            catch
            {
                return 0;
            }
        }

        /// <summary>Sends a 64-byte payload to every connected cooler.</summary>
        public void Send(byte[] payload)
        {
            bool changed = false;
            lock (_lock)
            {
                for (int i = _connections.Count - 1; i >= 0; i--)
                {
                    var c = _connections[i];
                    var report = new byte[c.ReportLength];
                    report[0] = c.ReportId;
                    Array.Copy(payload, 0, report, 1, Math.Min(payload.Length, report.Length - 1));
                    try
                    {
                        c.Stream.Write(report);
                    }
                    catch (TimeoutException)
                    {
                        // Device busy, try again next tick.
                    }
                    catch
                    {
                        try { c.Stream.Dispose(); } catch { }
                        _connections.RemoveAt(i);
                        changed = true;
                    }
                }
            }
            if (changed)
            {
                _rescanRequested = true;
                ConnectionChanged?.Invoke(this, EventArgs.Empty);
            }
        }

        public void Dispose()
        {
            lock (_lock)
            {
                foreach (var c in _connections)
                    try { c.Stream.Dispose(); } catch { }
                _connections.Clear();
            }
        }
    }
}
