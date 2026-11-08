using System;
using MajestyMonitor.Core;

// Checks the HID payload byte-for-byte against the layout used by the vendor app v1.2.
internal static class Program
{
    private static int _failures;

    private static void Eq(string name, int expected, int actual)
    {
        if (expected != actual)
        {
            _failures++;
            Console.WriteLine($"FAIL {name}: expected {expected}, got {actual}");
        }
    }

    private static int Main()
    {
        var s = new Snapshot();
        s[MetricKind.CpuTemp] = 54.4;
        s[MetricKind.CpuLoad] = 37.8;
        s[MetricKind.CpuPower] = 145.3;
        s[MetricKind.CpuClock] = 4725.6;
        s[MetricKind.CpuVoltage] = 1.364;
        s[MetricKind.GpuTemp] = 61.6;
        s[MetricKind.GpuLoad] = 99.2;
        s[MetricKind.GpuPower] = 212.7;
        s[MetricKind.GpuClock] = 2610;
        s[MetricKind.Fan1] = 1530;
        s[MetricKind.Fan2] = 980;
        s[MetricKind.RamLoad] = 46.6;
        var now = new DateTime(2026, 11, 8, 14, 5, 9); // a Sunday

        var p = CoolerProtocol.BuildPayload(s, now, fahrenheit: false);
        Eq("length", 64, p.Length);
        Eq("hdr0", 0, p[0]); Eq("hdr1", 1, p[1]); Eq("hdr2", 2, p[2]);
        int[] expected =
        {
            54, 0, 0,      // CPU temp, fraction, unit
            37,            // CPU load
            45, 30,        // CPU power 145.3 -> 45 / .30 (hundreds in v31)
            47, 25,        // CPU clock 4725 -> 47 / 25
            1, 36,         // CPU voltage 1.36
            62, 0, 0,      // GPU temp
            99,            // GPU load
            12, 70,        // GPU power 212.7 -> 12 / .70 (hundreds in v32)
            26, 10,        // GPU clock 2610
            15, 30,        // fan 1 1530
            9, 80,         // fan 2 980
            20, 26, 11, 8, 14, 5, 9, 0, // date/time, Sunday = 0
            47,            // RAM
            1, 2,          // power hundreds CPU / GPU
        };
        for (int i = 0; i < expected.Length; i++)
            Eq("v" + i, expected[i], p[3 + i]);
        for (int i = 3 + expected.Length; i < 64; i++)
            Eq("pad" + i, 0, p[i]);

        var pf = CoolerProtocol.BuildPayload(s, now, fahrenheit: true);
        Eq("F cpu", 130, pf[3 + 0]);  // 54.4 °C = 129.9 °F
        Eq("F unit", 1, pf[3 + 2]);
        Eq("F gpu", 143, pf[3 + 10]); // 61.6 °C = 142.9 °F
        Eq("F unit2", 1, pf[3 + 12]);

        var empty = CoolerProtocol.BuildPayload(new Snapshot(), now, false);
        Eq("empty cpu", 0, empty[3]);

        var off = CoolerProtocol.BuildShutdown();
        Eq("shutdown", 15, off[1]);
        Eq("shutdown len", 64, off.Length);

        Console.WriteLine(_failures == 0 ? "All protocol checks passed." : $"{_failures} check(s) failed.");
        return _failures == 0 ? 0 : 1;
    }
}
