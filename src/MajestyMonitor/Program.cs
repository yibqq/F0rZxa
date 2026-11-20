using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Linq;
using System.Threading;
using System.Windows.Forms;
using MajestyMonitor.Core;
using MajestyMonitor.UI;

namespace MajestyMonitor
{
    internal static class Program
    {
        private const string MutexName = "Local\\MajestyAirCoolerMonitor";
        private const string ShowEventName = "Local\\MajestyAirCoolerMonitor.Show";

        private static Icon _icon;

        public static Icon AppIcon
        {
            get
            {
                if (_icon == null)
                {
                    try
                    {
                        using (var s = typeof(Program).Assembly.GetManifestResourceStream("MajestyMonitor.app.ico"))
                            _icon = s != null ? new Icon(s) : SystemIcons.Application;
                    }
                    catch
                    {
                        _icon = SystemIcons.Application;
                    }
                }
                return _icon;
            }
        }

        private static Image _logo;

        /// <summary>High resolution logo for the header.</summary>
        public static Image Logo
        {
            get
            {
                if (_logo == null)
                {
                    try
                    {
                        using (var s = typeof(Program).Assembly.GetManifestResourceStream("MajestyMonitor.app.png"))
                            _logo = s != null ? new Bitmap(s) : AppIcon.ToBitmap();
                    }
                    catch
                    {
                        _logo = AppIcon.ToBitmap();
                    }
                }
                return _logo;
            }
        }

        /// <summary>The user's custom logo if one is set and readable, otherwise the default logo.</summary>
        public static Image LoadBrandLogo(AppSettings settings)
        {
            var path = settings?.BrandLogo;
            if (!string.IsNullOrEmpty(path) && File.Exists(path))
            {
                try
                {
                    // Load from a memory copy so the file is not kept locked.
                    using (var ms = new MemoryStream(File.ReadAllBytes(path)))
                    using (var img = Image.FromStream(ms))
                        return new Bitmap(img);
                }
                catch (Exception ex)
                {
                    Log("Custom logo unreadable: " + ex.Message);
                }
            }
            return Logo;
        }

        /// <summary>Copies a chosen image into the settings folder and returns the new path, or null if it isn't an image.</summary>
        public static string ImportBrandLogo(string source)
        {
            try
            {
                using (var ms = new MemoryStream(File.ReadAllBytes(source)))
                using (var img = Image.FromStream(ms))
                {
                    // Keep at most 512 px on the long side; save as PNG to preserve transparency.
                    float k = Math.Min(1f, 512f / Math.Max(img.Width, img.Height));
                    using (var bmp = new Bitmap(Math.Max(1, (int)(img.Width * k)), Math.Max(1, (int)(img.Height * k))))
                    {
                        using (var g = Graphics.FromImage(bmp))
                        {
                            g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                            g.DrawImage(img, 0, 0, bmp.Width, bmp.Height);
                        }
                        Directory.CreateDirectory(AppSettings.Folder);
                        foreach (var old in Directory.GetFiles(AppSettings.Folder, "logo_*.png"))
                            try { File.Delete(old); } catch { }
                        var dest = Path.Combine(AppSettings.Folder, "logo_" + DateTime.Now.Ticks + ".png");
                        bmp.Save(dest, ImageFormat.Png);
                        return dest;
                    }
                }
            }
            catch (Exception ex)
            {
                Log("Logo import failed: " + ex.Message);
                return null;
            }
        }

        [STAThread]
        private static int Main(string[] args)
        {
            bool Has(string flag) => args.Any(a => string.Equals(a, flag, StringComparison.OrdinalIgnoreCase));
            string Arg(string flag)
            {
                int i = Array.FindIndex(args, a => string.Equals(a, flag, StringComparison.OrdinalIgnoreCase));
                return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
            }

            // Called by the uninstaller.
            if (Has("--uninstall-cleanup"))
            {
                AutoStart.Remove();
                CloseOtherInstances();
                return 0;
            }

            // Called by the installer before files are replaced.
            if (Has("--close-running"))
            {
                CloseOtherInstances();
                return 0;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.ThreadException += (s, e) => Fatal(e.Exception);
            AppDomain.CurrentDomain.UnhandledException += (s, e) => Fatal(e.ExceptionObject as Exception);

            var settings = AppSettings.Load();
            // --lang is a preview/demo helper; it never changes the saved settings.
            bool preview = Has("--demo") || Arg("--screenshot") != null;
            settings.ReadOnly = preview;
            if (preview && Arg("--brand") is string brandArg)
                settings.BrandName = brandArg;
            if (preview && Arg("--logo") is string logoArg)
                settings.BrandLogo = logoArg;
            if (preview && Arg("--lang") is string lang)
                settings.Language = lang == "en" ? "en" : "ar";
            Strings.Lang = settings.Language;

            // Render a high resolution preview image and exit (used for documentation).
            if (Arg("--screenshot") is string shotPath)
            {
                if (Environment.GetEnvironmentVariable("MAJESTY_TRACE") == "1")
                    { DashboardView.Trace = m => File.AppendAllText(shotPath + ".trace", m + Environment.NewLine); DashboardView.Trace("fonts: " + Theme.UiFamily + " / " + Theme.ArabicFamily + " / " + Theme.NumFamily); }
                float scale = float.TryParse(Arg("--scale"), System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var sc) ? sc : 2f;
                using (var bmp = MainForm.RenderPreview(new DemoSensorSource(), settings, scale))
                    bmp.Save(shotPath, ImageFormat.Png);
                return 0;
            }

            bool demo = Has("--demo");

            using (var mutex = new Mutex(true, demo ? MutexName + ".Demo" : MutexName, out bool first))
            {
                if (!first)
                {
                    // Already running: bring the existing window forward.
                    try
                    {
                        using (var ev = EventWaitHandle.OpenExisting(ShowEventName))
                            ev.Set();
                    }
                    catch { }
                    return 0;
                }

                if (settings.IsFirstRun && !demo)
                {
                    settings.Save();
                    if (settings.AutoStart)
                        AutoStart.Set(true);
                }

                ISensorSource source = demo ? (ISensorSource)new DemoSensorSource() : new HardwareSensorSource();
                bool startHidden = (Has("--minimized") || settings.StartMinimized) && !demo;
                var form = new MainForm(settings, source, demo, startHidden);

                using (var showEvent = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName))
                {
                    var listener = new Thread(() =>
                    {
                        while (true)
                        {
                            showEvent.WaitOne();
                            try { form.ShowFromTray(); } catch { return; }
                        }
                    }) { IsBackground = true };
                    listener.Start();

                    Application.Run(form);
                }
                GC.KeepAlive(mutex);
            }
            return 0;
        }

        private static void CloseOtherInstances()
        {
            var me = System.Diagnostics.Process.GetCurrentProcess();
            foreach (var p in System.Diagnostics.Process.GetProcessesByName(me.ProcessName))
            {
                if (p.Id == me.Id) continue;
                try
                {
                    if (!p.CloseMainWindow() || !p.WaitForExit(2000))
                        p.Kill();
                    p.WaitForExit(3000);
                }
                catch { }
            }
        }

        public static void Log(string message)
        {
            try
            {
                Directory.CreateDirectory(AppSettings.Folder);
                var path = Path.Combine(AppSettings.Folder, "log.txt");
                if (File.Exists(path) && new FileInfo(path).Length > 512 * 1024)
                    File.Delete(path);
                File.AppendAllText(path, DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss") + "  " + message + Environment.NewLine);
            }
            catch { }
        }

        private static bool _fatalShown;

        private static void Fatal(Exception ex)
        {
            Log("Unhandled: " + ex);
            if (_fatalShown) return;
            _fatalShown = true;
            MessageBox.Show(ex?.Message ?? "Unknown error", "MAJESTY · " + Strings.Get("Error"), MessageBoxButtons.OK, MessageBoxIcon.Error);
        }
    }
}
