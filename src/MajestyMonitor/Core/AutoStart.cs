using System;
using System.Diagnostics;
using Microsoft.Win32;

namespace MajestyMonitor.Core
{
    /// <summary>
    /// Starts the app at logon through a scheduled task with highest privileges, so the
    /// administrator-only sensors work without a UAC prompt on every boot.
    /// </summary>
    public static class AutoStart
    {
        private const string TaskName = "MAJESTY Air Cooler Monitor";
        private const string LegacyRunValue = "MAJESTY Air Cooler Monitor";

        public static bool IsEnabled()
        {
            return Run("/Query /TN \"" + TaskName + "\"") == 0;
        }

        public static bool Set(bool enable)
        {
            RemoveLegacyRunKey();
            if (!enable)
                return Run("/Delete /TN \"" + TaskName + "\" /F") == 0 || !IsEnabled();

            string exe = Process.GetCurrentProcess().MainModule.FileName;
            string args = "/Create /TN \"" + TaskName + "\" /TR \"\\\"" + exe + "\\\" --minimized\" /SC ONLOGON /RL HIGHEST /F";
            return Run(args) == 0;
        }

        public static void Remove()
        {
            Run("/Delete /TN \"" + TaskName + "\" /F");
            RemoveLegacyRunKey();
        }

        private static void RemoveLegacyRunKey()
        {
            try
            {
                using (var k = Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run", true))
                    k?.DeleteValue(LegacyRunValue, false);
            }
            catch { }
        }

        private static int Run(string args)
        {
            try
            {
                var psi = new ProcessStartInfo("schtasks.exe", args)
                {
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                };
                using (var p = Process.Start(psi))
                {
                    p.StandardOutput.ReadToEnd();
                    p.StandardError.ReadToEnd();
                    p.WaitForExit(10000);
                    return p.ExitCode;
                }
            }
            catch
            {
                return -1;
            }
        }
    }
}
