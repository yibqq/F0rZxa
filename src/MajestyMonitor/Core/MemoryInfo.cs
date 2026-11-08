using System.Runtime.InteropServices;

namespace MajestyMonitor.Core
{
    internal static class MemoryInfo
    {
        [StructLayout(LayoutKind.Sequential)]
        private struct MEMORYSTATUSEX
        {
            public uint dwLength;
            public uint dwMemoryLoad;
            public ulong ullTotalPhys;
            public ulong ullAvailPhys;
            public ulong ullTotalPageFile;
            public ulong ullAvailPageFile;
            public ulong ullTotalVirtual;
            public ulong ullAvailVirtual;
            public ulong ullAvailExtendedVirtual;
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool GlobalMemoryStatusEx(ref MEMORYSTATUSEX buffer);

        public static bool TryGet(out double loadPercent, out double usedGb, out double totalGb)
        {
            var m = new MEMORYSTATUSEX { dwLength = (uint)Marshal.SizeOf(typeof(MEMORYSTATUSEX)) };
            loadPercent = usedGb = totalGb = 0;
            if (!GlobalMemoryStatusEx(ref m) || m.ullTotalPhys == 0)
                return false;
            const double gb = 1024.0 * 1024.0 * 1024.0;
            totalGb = m.ullTotalPhys / gb;
            usedGb = (m.ullTotalPhys - m.ullAvailPhys) / gb;
            loadPercent = usedGb / totalGb * 100.0;
            return true;
        }
    }
}
