using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows.Forms;
using MajestyMonitor.Core;

namespace MajestyMonitor.UI
{
    internal sealed class MainForm : Form
    {
        private readonly AppSettings _settings;
        private readonly ISensorSource _source;
        private readonly CoolerLink _link;
        private readonly bool _demo;
        private readonly DashboardView _view = new DashboardView();
        private readonly System.Windows.Forms.Timer _uiTimer = new System.Windows.Forms.Timer();
        private readonly NotifyIcon _tray = new NotifyIcon();
        private readonly Stopwatch _clock = Stopwatch.StartNew();
        private float _scale;
        private readonly object _snapLock = new object();
        private readonly Queue<Snapshot> _pending = new Queue<Snapshot>();

        private Thread _sensorThread;
        private Thread _deviceThread;
        private volatile bool _stopping;
        private volatile Snapshot _latest;
        private bool _reallyClose;
        private bool _trayHintShown;
        private double _lastTick;
        private bool _startHidden;

        public MainForm(AppSettings settings, ISensorSource source, bool demo, bool startHidden)
        {
            _settings = settings;
            _source = source;
            _demo = demo;
            _startHidden = startHidden;
            _link = demo ? null : new CoolerLink();

            Text = "MAJESTY Air Cooler Monitor";
            Icon = Program.AppIcon;
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            DoubleBuffered = true;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.UserPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.ResizeRedraw, true);
            BackColor = Theme.Background;
            KeyPreview = true;

            using (var g = CreateGraphics())
                _scale = Math.Max(1f, g.DpiX / 96f);
            // Shrink to fit small screens (e.g. 1366×768 laptops at 125 %).
            var wa = Screen.PrimaryScreen.WorkingArea;
            float fit = Math.Min((wa.Width - 20) / DashboardView.W, (wa.Height - 20) / DashboardView.H);
            if (fit > 0.5f && fit < _scale)
                _scale = fit;
            ClientSize = new Size((int)Math.Round(DashboardView.W * _scale), (int)Math.Round(DashboardView.H * _scale));

            _view.Logo = Program.Logo;
            _view.Demo = demo;
            _view.Version = Application.ProductVersion.Split('.').Length >= 2
                ? string.Join(".", Application.ProductVersion.Split('.'), 0, 2)
                : Application.ProductVersion;
            ApplySettingsToView();

            BuildTray();

            _uiTimer.Interval = 33;
            _uiTimer.Tick += (s, e) => OnUiTick();
        }

        protected override CreateParams CreateParams
        {
            get
            {
                const int CS_DROPSHADOW = 0x20000;
                const int WS_MINIMIZEBOX = 0x20000;
                var cp = base.CreateParams;
                cp.ClassStyle |= CS_DROPSHADOW;
                cp.Style |= WS_MINIMIZEBOX; // lets the taskbar button minimise/restore a borderless window
                return cp;
            }
        }

        protected override void SetVisibleCore(bool value)
        {
            if (_startHidden)
            {
                _startHidden = false;
                if (!IsHandleCreated) CreateHandle();
                value = false;
                OnLoadStartup();
            }
            base.SetVisibleCore(value);
        }

        private bool _started;

        protected override void OnLoad(EventArgs e)
        {
            base.OnLoad(e);
            var area = Screen.FromPoint(Cursor.Position).WorkingArea;
            Location = new Point(area.X + Math.Max(0, (area.Width - Width) / 2), area.Y + Math.Max(0, (area.Height - Height) / 2));
            OnLoadStartup();
            TryRoundCorners();
        }

        private void OnLoadStartup()
        {
            if (_started) return;
            _started = true;
            _sensorThread = new Thread(SensorLoop) { IsBackground = true, Name = "Sensors" };
            _sensorThread.Start();
            if (_link != null)
            {
                _deviceThread = new Thread(DeviceLoop) { IsBackground = true, Name = "Cooler" };
                _deviceThread.Start();
            }
            _uiTimer.Start();
        }

        private void ApplySettingsToView()
        {
            Strings.Lang = _settings.Language;
            _view.Fahrenheit = _settings.Fahrenheit;
            _view.SendToDevice = _settings.SendToDevice;
            if (_tray.ContextMenuStrip != null)
                BuildTrayMenu();
            Invalidate();
        }

        // ---- Background work ------------------------------------------------------------

        private void SensorLoop()
        {
            try
            {
                _source.Open();
            }
            catch (Exception ex)
            {
                Program.Log("Sensor open failed: " + ex);
            }

            while (!_stopping)
            {
                try
                {
                    var bindings = new Dictionary<MetricKind, string>(_settings.Bindings);
                    var snap = _source.Read(bindings);
                    _latest = snap;
                    lock (_snapLock)
                        _pending.Enqueue(snap);
                }
                catch (Exception ex)
                {
                    Program.Log("Sensor read failed: " + ex.Message);
                }
                Sleep(_settings.RefreshMs);
            }
        }

        private void DeviceLoop()
        {
            while (!_stopping)
            {
                try
                {
                    _link.EnsureConnected();
                    var snap = _latest;
                    if (_settings.SendToDevice && snap != null && _link.IsConnected)
                        _link.Send(CoolerProtocol.BuildPayload(snap, DateTime.Now, _settings.Fahrenheit));
                }
                catch (Exception ex)
                {
                    Program.Log("Device loop: " + ex.Message);
                }
                Sleep(300);
            }
        }

        private void Sleep(int ms)
        {
            var until = DateTime.UtcNow.AddMilliseconds(ms);
            while (!_stopping && DateTime.UtcNow < until)
                Thread.Sleep(50);
        }

        // ---- UI ---------------------------------------------------------------------------

        private void OnUiTick()
        {
            lock (_snapLock)
            {
                while (_pending.Count > 0)
                {
                    _view.Push(_pending.Dequeue());
                    _view.SensorsReady = true;
                }
            }
            _view.Warning = _source.Warning;
            _view.ConnectedCount = _demo ? 1 : _link.ConnectedCount;

            double now = _clock.Elapsed.TotalSeconds;
            double dt = Math.Min(0.2, now - _lastTick);
            _lastTick = now;
            _view.Animate(dt);

            UpdateTrayText();
            if (Visible && WindowState != FormWindowState.Minimized)
                Invalidate();
        }

        protected override void OnPaint(PaintEventArgs e)
        {
            e.Graphics.ScaleTransform(_scale, _scale);
            _view.Paint(e.Graphics);
        }

        protected override void OnPaintBackground(PaintEventArgs e)
        {
            // Everything is painted in OnPaint.
        }

        private string HitTest(Point p)
        {
            var lp = new PointF(p.X / _scale, p.Y / _scale);
            foreach (var kv in _view.HitAreas)
                if (kv.Value.Contains(lp))
                    return kv.Key;
            return null;
        }

        protected override void OnMouseMove(MouseEventArgs e)
        {
            base.OnMouseMove(e);
            var hit = HitTest(e.Location);
            if (hit != _view.HoverKey)
            {
                _view.HoverKey = hit;
                Cursor = hit != null ? Cursors.Hand : Cursors.Default;
                Invalidate();
            }
        }

        protected override void OnMouseLeave(EventArgs e)
        {
            base.OnMouseLeave(e);
            _view.HoverKey = null;
            Cursor = Cursors.Default;
        }

        [DllImport("user32.dll")] private static extern bool ReleaseCapture();
        [DllImport("user32.dll")] private static extern IntPtr SendMessage(IntPtr hWnd, int msg, IntPtr wParam, IntPtr lParam);

        protected override void OnMouseDown(MouseEventArgs e)
        {
            base.OnMouseDown(e);
            if (e.Button != MouseButtons.Left)
                return;
            var hit = HitTest(e.Location);
            switch (hit)
            {
                case "close": CloseOrHide(); break;
                case "min": WindowState = FormWindowState.Minimized; break;
                case "settings": OpenSettings(); break;
                case "unit":
                    _settings.Fahrenheit = !_settings.Fahrenheit;
                    _settings.Save();
                    ApplySettingsToView();
                    break;
                case "install": InstallPawnIo(); break;
                default:
                    if (e.Y / _scale < 70)
                    {
                        // Drag the borderless window by its header.
                        ReleaseCapture();
                        SendMessage(Handle, 0xA1 /*WM_NCLBUTTONDOWN*/, (IntPtr)2 /*HTCAPTION*/, IntPtr.Zero);
                    }
                    break;
            }
        }

        protected override void OnKeyDown(KeyEventArgs e)
        {
            base.OnKeyDown(e);
            if (e.KeyCode == Keys.Escape) CloseOrHide();
            if (e.KeyCode == Keys.F2 || (e.Control && e.KeyCode == Keys.Oemcomma)) OpenSettings();
        }

        private void CloseOrHide()
        {
            if (_settings.CloseToTray && !_demo)
            {
                HideToTray();
            }
            else
            {
                _reallyClose = true;
                Close();
            }
        }

        private void HideToTray()
        {
            Hide();
            if (!_trayHintShown)
            {
                _trayHintShown = true;
                _tray.ShowBalloonTip(3000, "MAJESTY", Strings.Get("TrayHint"), ToolTipIcon.Info);
            }
        }

        public void ShowFromTray()
        {
            if (InvokeRequired)
            {
                BeginInvoke((Action)ShowFromTray);
                return;
            }
            Show();
            if (WindowState == FormWindowState.Minimized)
                WindowState = FormWindowState.Normal;
            Activate();
            BringToFront();
        }

        private void OpenSettings()
        {
            ShowFromTray();
            using (var dlg = new SettingsForm(_settings, _source))
            {
                bool wasAuto = _settings.AutoStart;
                if (dlg.ShowDialog(this) == DialogResult.OK)
                {
                    _settings.Save();
                    if (_settings.AutoStart != wasAuto || (_settings.AutoStart && !AutoStart.IsEnabled()))
                        AutoStart.Set(_settings.AutoStart);
                    ApplySettingsToView();
                }
            }
        }

        private void InstallPawnIo()
        {
            try
            {
                var psi = new ProcessStartInfo("winget.exe",
                    "install --id namazso.PawnIO -e --accept-package-agreements --accept-source-agreements")
                { UseShellExecute = true };
                Process.Start(psi);
            }
            catch
            {
                try { Process.Start(new ProcessStartInfo("https://pawnio.eu/") { UseShellExecute = true }); } catch { }
            }
        }

        private void TryRoundCorners()
        {
            try
            {
                int pref = 2; // DWMWCP_ROUND (Windows 11)
                DwmSetWindowAttribute(Handle, 33, ref pref, sizeof(int));
            }
            catch { }
        }

        [DllImport("dwmapi.dll")]
        private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);

        // ---- Tray -----------------------------------------------------------------------

        private void BuildTray()
        {
            _tray.Icon = Program.AppIcon;
            _tray.Text = "MAJESTY Air Cooler Monitor";
            _tray.Visible = !_demo;
            _tray.DoubleClick += (s, e) => ShowFromTray();
            BuildTrayMenu();
        }

        private void BuildTrayMenu()
        {
            var old = _tray.ContextMenuStrip;
            var menu = new ContextMenuStrip { RightToLeft = Strings.Rtl ? RightToLeft.Yes : RightToLeft.No };
            menu.Items.Add(Strings.Get("Show"), null, (s, e) => ShowFromTray());
            menu.Items.Add(Strings.Get("Settings"), null, (s, e) => OpenSettings());
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add(Strings.Get("Exit"), null, (s, e) => { _reallyClose = true; Close(); });
            _tray.ContextMenuStrip = menu;
            old?.Dispose();
        }

        private DateTime _lastTrayText;

        private void UpdateTrayText()
        {
            if ((DateTime.Now - _lastTrayText).TotalSeconds < 2 || _latest == null)
                return;
            _lastTrayText = DateTime.Now;
            string unit = _settings.Fahrenheit ? "°F" : "°C";
            string fmt(MetricKind k) => _latest[k].HasValue
                ? Math.Round(CoolerProtocol.ToUnit(_latest[k].Value, _settings.Fahrenheit)) + unit
                : "--";
            string text = $"MAJESTY  ·  CPU {fmt(MetricKind.CpuTemp)}  ·  GPU {fmt(MetricKind.GpuTemp)}";
            _tray.Text = text.Length > 63 ? text.Substring(0, 63) : text;
        }

        // ---- Shutdown -------------------------------------------------------------------

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (!_reallyClose && e.CloseReason == CloseReason.UserClosing && _settings.CloseToTray && !_demo)
            {
                e.Cancel = true;
                HideToTray();
                return;
            }
            base.OnFormClosing(e);
        }

        protected override void OnFormClosed(FormClosedEventArgs e)
        {
            _stopping = true;
            _uiTimer.Stop();
            _tray.Visible = false;
            try { _deviceThread?.Join(1500); } catch { }
            try
            {
                if (_link != null && _settings.SendToDevice)
                    _link.Send(CoolerProtocol.BuildShutdown());
            }
            catch { }
            _link?.Dispose();
            try { _sensorThread?.Join(1500); } catch { }
            _source.Dispose();
            _tray.Dispose();
            base.OnFormClosed(e);
        }

        // ---- Screenshot helper (used by --screenshot) ----------------------------------

        internal static Bitmap RenderPreview(DemoSensorSource demo, AppSettings settings, float scale)
        {
            Strings.Lang = settings.Language;
            var view = new DashboardView
            {
                Logo = Program.Logo,
                Fahrenheit = settings.Fahrenheit,
                SendToDevice = true,
                ConnectedCount = 1,
                SensorsReady = true,
                Demo = false,
            };
            for (int i = 0; i < DashboardView.HistoryLength; i++)
                view.Push(demo.At(i * 1.0));
            for (int i = 0; i < 40; i++)
                view.Animate(0.25);
            var bmp = new Bitmap((int)(DashboardView.W * scale), (int)(DashboardView.H * scale));
            using (var g = Graphics.FromImage(bmp))
            {
                g.ScaleTransform(scale, scale);
                view.Paint(g);
            }
            return bmp;
        }
    }
}
