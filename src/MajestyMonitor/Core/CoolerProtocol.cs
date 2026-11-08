using System;

namespace MajestyMonitor.Core
{
    /// <summary>
    /// Builds the 64-byte HID report understood by the MAJESTY cooler display
    /// (USB VID 0x5131 / PID 0x2007). Layout recovered from the vendor app v1.2:
    ///
    ///   [0]=0x00 [1]=0x01 [2]=0x02 (header), then 33 value bytes v[0..32] at offset 3:
    ///   v0  CPU temp (integer)        v1  CPU temp fraction x100   v2  unit (0=°C, 1=°F)
    ///   v3  CPU load %                v4  CPU power  % 100         v5  CPU power fraction x100
    ///   v6  CPU clock / 100           v7  CPU clock % 100          v8  CPU voltage (integer)
    ///   v9  CPU voltage fraction x100 v10 GPU temp (integer)       v11 GPU temp fraction x100
    ///   v12 unit (0=°C, 1=°F)         v13 GPU load %               v14 GPU power % 100
    ///   v15 GPU power fraction x100   v16 GPU clock / 100          v17 GPU clock % 100
    ///   v18 fan 1 RPM / 100           v19 fan 1 RPM % 100          v20 fan 2 RPM / 100
    ///   v21 fan 2 RPM % 100           v22 year / 100               v23 year % 100
    ///   v24 month  v25 day  v26 hour  v27 minute  v28 second  v29 day of week (Sunday = 0)
    ///   v30 RAM load %                v31 CPU power / 100          v32 GPU power / 100
    ///
    /// Sending a report whose byte [1] is 0x0F tells the display that the PC app is closing.
    /// </summary>
    public static class CoolerProtocol
    {
        public const int VendorId = 0x5131;
        public const int ProductId = 0x2007;
        public const int PayloadLength = 64;
        public const int ValueCount = 33;

        public static byte[] BuildPayload(Snapshot s, DateTime now, bool fahrenheit)
        {
            var v = new int[ValueCount];

            // Temperatures: the vendor app sends whole degrees (fraction byte always 0).
            double cpuT = Math.Round(ToUnit(s.Get(MetricKind.CpuTemp), fahrenheit));
            double gpuT = Math.Round(ToUnit(s.Get(MetricKind.GpuTemp), fahrenheit));

            v[0] = (int)cpuT;
            v[1] = 0;
            v[2] = fahrenheit ? 1 : 0;
            v[3] = (int)Math.Floor(s.Get(MetricKind.CpuLoad));

            SplitPower(s.Get(MetricKind.CpuPower), out v[31], out v[4], out v[5]);
            SplitHundreds(s.Get(MetricKind.CpuClock), out v[6], out v[7]);
            SplitFraction(Math.Round(s.Get(MetricKind.CpuVoltage), 2), out v[8], out v[9]);

            v[10] = (int)gpuT;
            v[11] = 0;
            v[12] = fahrenheit ? 1 : 0;
            v[13] = (int)Math.Floor(s.Get(MetricKind.GpuLoad));

            SplitPower(s.Get(MetricKind.GpuPower), out v[32], out v[14], out v[15]);
            SplitHundreds(s.Get(MetricKind.GpuClock), out v[16], out v[17]);
            SplitHundreds(s.Get(MetricKind.Fan1), out v[18], out v[19]);
            SplitHundreds(s.Get(MetricKind.Fan2), out v[20], out v[21]);

            v[22] = now.Year / 100;
            v[23] = now.Year % 100;
            v[24] = now.Month;
            v[25] = now.Day;
            v[26] = now.Hour;
            v[27] = now.Minute;
            v[28] = now.Second;
            v[29] = (int)now.DayOfWeek;
            v[30] = (int)Math.Round(s.Get(MetricKind.RamLoad));

            var payload = new byte[PayloadLength];
            payload[0] = 0x00;
            payload[1] = 0x01;
            payload[2] = 0x02;
            for (int i = 0; i < ValueCount; i++)
                payload[3 + i] = ClampByte(v[i]);
            return payload;
        }

        public static byte[] BuildShutdown()
        {
            var payload = new byte[PayloadLength];
            payload[1] = 0x0F;
            return payload;
        }

        public static double ToUnit(double celsius, bool fahrenheit) => fahrenheit ? celsius * 1.8 + 32.0 : celsius;

        private static void SplitPower(double watts, out int hundreds, out int rest, out int fraction)
        {
            watts = Math.Max(0, Math.Round(watts, 1));
            double whole = Math.Floor(watts);
            hundreds = (int)Math.Floor(whole / 100.0);
            rest = (int)(whole % 100.0);
            fraction = (int)Math.Round((watts - whole) * 100.0);
        }

        private static void SplitHundreds(double value, out int high, out int low)
        {
            double whole = Math.Floor(Math.Max(0, value));
            high = (int)Math.Floor(whole / 100.0);
            low = (int)(whole % 100.0);
        }

        private static void SplitFraction(double value, out int whole, out int fraction)
        {
            value = Math.Max(0, value);
            double w = Math.Floor(value);
            whole = (int)w;
            fraction = (int)Math.Round((value - w) * 100.0);
            if (fraction >= 100) { whole++; fraction = 0; }
        }

        private static byte ClampByte(int x) => (byte)(x < 0 ? 0 : x > 255 ? 255 : x);
    }
}
