using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;
using System.Linq;
using MajestyMonitor.Core;

namespace MajestyMonitor.UI
{
    /// <summary>
    /// Everything the dashboard shows, painted in a fixed 1180×760 logical canvas.
    /// Kept independent from the window so it can also render straight to a bitmap.
    /// </summary>
    internal sealed class DashboardView
    {
        public const float W = 1180f;
        public const float H = 760f;
        public const int HistoryLength = 120;

        // ---- State fed by the form -------------------------------------------------------
        public Snapshot Target = new Snapshot();
        public readonly double?[] Shown = new double?[MetricInfo.All.Length];
        public readonly Dictionary<MetricKind, List<double>> History = new Dictionary<MetricKind, List<double>>();
        public readonly Dictionary<MetricKind, double> SessionMax = new Dictionary<MetricKind, double>();
        public bool Fahrenheit;
        public bool SendToDevice = true;
        public int ConnectedCount;
        public bool SensorsReady;
        public bool Demo;
        public string Warning;
        public DateTime LastUpdate = DateTime.MinValue;
        public string HoverKey;
        public float Fan1Angle, Fan2Angle;
        public Image Logo;
        public string Version = "2.0";

        /// <summary>Clickable regions from the last paint, in logical coordinates.</summary>
        public readonly Dictionary<string, RectangleF> HitAreas = new Dictionary<string, RectangleF>();

        public DashboardView()
        {
            foreach (var k in MetricInfo.All)
                History[k] = new List<double>();
        }

        // ---- Data ------------------------------------------------------------------------

        public void Push(Snapshot s)
        {
            Target = s;
            LastUpdate = s.Time;
            foreach (var k in MetricInfo.All)
            {
                var v = s[k];
                if (!v.HasValue)
                    continue;
                var h = History[k];
                h.Add(v.Value);
                if (h.Count > HistoryLength)
                    h.RemoveAt(0);
                if (!SessionMax.TryGetValue(k, out var m) || v.Value > m)
                    SessionMax[k] = v.Value;
                if (!Shown[(int)k].HasValue)
                    Shown[(int)k] = v.Value;
            }
        }

        /// <summary>Eases displayed numbers toward the latest reading and spins the fans.</summary>
        public void Animate(double dtSeconds)
        {
            double f = 1 - Math.Exp(-dtSeconds * 6.0);
            foreach (var k in MetricInfo.All)
            {
                var target = Target[k];
                if (!target.HasValue)
                {
                    Shown[(int)k] = null;
                    continue;
                }
                var cur = Shown[(int)k] ?? target.Value;
                Shown[(int)k] = cur + (target.Value - cur) * f;
            }
            Fan1Angle = (Fan1Angle + (float)(Get(MetricKind.Fan1) / 60.0 * 360.0 * dtSeconds * 0.12)) % 360f;
            Fan2Angle = (Fan2Angle + (float)(Get(MetricKind.Fan2) / 60.0 * 360.0 * dtSeconds * 0.12)) % 360f;
        }

        private double Get(MetricKind k) => Shown[(int)k] ?? 0;
        private bool Has(MetricKind k) => Shown[(int)k].HasValue;

        // ---- Layout helpers --------------------------------------------------------------

        private static bool Rtl => Strings.Rtl;

        /// <summary>Mirrors a rectangle horizontally in Arabic so the layout reads right-to-left.</summary>
        private static RectangleF M(RectangleF r) => Rtl ? new RectangleF(W - r.Right, r.Y, r.Width, r.Height) : r;
        private static RectangleF M(float x, float y, float w, float h) => M(new RectangleF(x, y, w, h));
        private static PointF M(PointF p) => Rtl ? new PointF(W - p.X, p.Y) : p;

        private Font UiFont(float size, FontStyle style = FontStyle.Regular) =>
            new Font(Rtl ? Theme.ArabicFamily : Theme.UiFamily, size, style, GraphicsUnit.Pixel);

        private static Font NumFont(float size, FontStyle style = FontStyle.Regular) =>
            new Font(Theme.NumFamily, size, style, GraphicsUnit.Pixel);

        private static Font LatinFont(float size, FontStyle style = FontStyle.Regular) =>
            new Font(Theme.UiFamily, size, style, GraphicsUnit.Pixel);

        private enum Align { Start, Center, End }

        private static void Text(Graphics g, string s, Font f, Color c, RectangleF r, Align h = Align.Start, Align v = Align.Center, bool rtlText = true)
        {
            using (var sf = new StringFormat(StringFormatFlags.NoWrap))
            using (var b = new SolidBrush(c))
            {
                sf.Trimming = StringTrimming.EllipsisCharacter;
                bool rtl = Rtl && rtlText;
                if (rtl)
                    sf.FormatFlags |= StringFormatFlags.DirectionRightToLeft;
                // With DirectionRightToLeft, Near is the right edge — exactly "Start" in Arabic.
                StringAlignment ha = h == Align.Center ? StringAlignment.Center : h == Align.Start ? StringAlignment.Near : StringAlignment.Far;
                if (Rtl && !rtl && h != Align.Center)
                    ha = ha == StringAlignment.Near ? StringAlignment.Far : StringAlignment.Near;
                sf.Alignment = ha;
                sf.LineAlignment = v == Align.Center ? StringAlignment.Center : v == Align.Start ? StringAlignment.Near : StringAlignment.Far;
                g.DrawString(s, f, b, r, sf);
            }
        }

        private static SizeF Measure(Graphics g, string s, Font f)
        {
            using (var sf = new StringFormat(StringFormat.GenericTypographic) { FormatFlags = StringFormatFlags.MeasureTrailingSpaces | StringFormatFlags.NoWrap })
                return g.MeasureString(s, f, 2000, sf);
        }

        private string TempUnit => Fahrenheit ? "°F" : "°C";
        private double T(double c) => CoolerProtocol.ToUnit(c, Fahrenheit);

        // ---- Paint -----------------------------------------------------------------------

        public static Action<string> Trace = _ => { };

        public void Paint(Graphics g)
        {
            Theme.Setup(g);
            HitAreas.Clear();
            Trace("bg");
            PaintBackground(g);
            Trace("header");
            PaintHeader(g);
            Trace("panels");

            PaintChipPanel(g, M(20, 84, 560, 320), "CPU", Target.CpuName, Theme.Cyan,
                MetricKind.CpuTemp, MetricKind.CpuLoad, MetricKind.CpuPower, MetricKind.CpuClock, MetricKind.CpuVoltage);
            PaintChipPanel(g, M(600, 84, 560, 320), "GPU", Target.GpuName, Theme.Violet,
                MetricKind.GpuTemp, MetricKind.GpuLoad, MetricKind.GpuPower, MetricKind.GpuClock, MetricKind.GpuMemLoad);

            Trace("history");
            PaintHistory(g, M(20, 420, 560, 292));
            Trace("fans");
            PaintFans(g, M(600, 420, 270, 292));
            Trace("ram");
            PaintRam(g, M(890, 420, 270, 292));
            Trace("footer");
            PaintFooter(g);
            Trace("done");
        }

        private void PaintBackground(Graphics g)
        {
            g.Clear(Theme.Background);
            Glow(g, new RectangleF(-260, -300, 820, 640), Theme.Cyan.WithAlpha(34));
            Glow(g, new RectangleF(W - 560, H - 380, 820, 640), Theme.Violet.WithAlpha(34));
        }

        private static void Glow(Graphics g, RectangleF r, Color c)
        {
            using (var path = new GraphicsPath())
            {
                path.AddEllipse(r);
                using (var b = new PathGradientBrush(path) { CenterColor = c, SurroundColors = new[] { c.WithAlpha(0) } })
                    g.FillPath(b, path);
            }
        }

        private void Card(Graphics g, RectangleF r, float radius = 18)
        {
            using (var b = new LinearGradientBrush(r, Theme.CardHi, Theme.Card, LinearGradientMode.Vertical))
                g.FillRounded(b, r, radius);
            using (var p = new Pen(Theme.Border, 1f))
                g.DrawRounded(p, r, radius);
        }

        // Header -----------------------------------------------------------------------------

        private void PaintHeader(Graphics g)
        {
            var logoRect = M(22, 15, 36, 36);
            if (Logo != null)
                g.DrawImage(Logo, logoRect);

            using (var fBrand = LatinFont(21, FontStyle.Bold))
            using (var fSub = UiFont(12.5f))
            {
                Text(g, "MAJESTY", fBrand, Theme.Text, M(68, 12, 200, 24), Align.Start, Align.Center, rtlText: false);
                Text(g, Strings.Get("AppSub"), fSub, Theme.Muted, M(69, 35, 260, 18));
            }

            // Window buttons (always at the "end" side)
            float x = W - 20 - 34;
            HeaderButton(g, "close", M(x, 16, 34, 32), DrawCloseGlyph, Theme.Red);
            x -= 38;
            HeaderButton(g, "min", M(x, 16, 34, 32), DrawMinGlyph, Theme.Border);
            x -= 46;
            HeaderButton(g, "settings", M(x, 16, 34, 32), DrawGearGlyph, Theme.Border);

            // °C / °F segmented toggle
            x -= 16 + 84;
            var seg = M(x, 18, 84, 28);
            using (var b = new SolidBrush(Theme.Card)) g.FillRounded(b, seg, 14);
            using (var p = new Pen(Theme.Border)) g.DrawRounded(p, seg, 14);
            var cRect = new RectangleF(seg.X + 3, seg.Y + 3, 39, 22);
            var fRect = new RectangleF(seg.X + 42, seg.Y + 3, 39, 22);
            var active = Fahrenheit ? fRect : cRect;
            using (var b = new LinearGradientBrush(active, Theme.Cyan, Theme.Violet, LinearGradientMode.Horizontal))
                g.FillRounded(b, active, 11);
            using (var f = LatinFont(12.5f, FontStyle.Bold))
            {
                Text(g, "°C", f, Fahrenheit ? Theme.Muted : Theme.Background, cRect, Align.Center, Align.Center, false);
                Text(g, "°F", f, Fahrenheit ? Theme.Background : Theme.Muted, fRect, Align.Center, Align.Center, false);
            }
            HitAreas["unit"] = seg;

            // Connection pill
            string status;
            Color dot;
            if (!SendToDevice)
            {
                status = Strings.Get("SendingOff");
                dot = Theme.Amber;
            }
            else if (ConnectedCount > 0)
            {
                status = ConnectedCount == 1 ? Strings.Get("Connected") : string.Format(Strings.Get("ConnectedN"), ConnectedCount);
                dot = Theme.Green;
            }
            else
            {
                status = Strings.Get("Disconnected");
                dot = Theme.Red;
            }

            using (var f = UiFont(13f, FontStyle.Bold))
            {
                float tw = Measure(g, status, f).Width;
                float pw = tw + 70;
                x -= 14 + pw;
                var pill = M(x, 16, pw, 32);
                using (var b = new SolidBrush(dot.WithAlpha(26))) g.FillRounded(b, pill, 16);
                using (var p = new Pen(dot.WithAlpha(90))) g.DrawRounded(p, pill, 16);

                var usb = M(x + 12, 22, 20, 20);
                DrawUsbGlyph(g, usb, dot);
                var dotR = M(x + pw - 20, 28, 8, 8);
                using (var b = new SolidBrush(dot.WithAlpha(60))) g.FillEllipse(b, RectangleF.Inflate(dotR, 4, 4));
                using (var b = new SolidBrush(dot)) g.FillEllipse(b, dotR);
                Text(g, status, f, Theme.Text, M(x + 38, 16, tw + 6, 32));
            }

            // Clock
            using (var fT = NumFont(20))
            using (var fD = UiFont(11.5f))
            {
                var now = DateTime.Now;
                float cx = x - 24 - 150;
                Text(g, now.ToString("HH:mm:ss", CultureInfo.InvariantCulture), fT, Theme.Text, M(cx - 60, 12, 210, 24), Align.Center, Align.Center, false);
                Text(g, Strings.Date(now), fD, Theme.Muted, M(cx - 60, 36, 210, 16), Align.Center);
            }
        }

        private void HeaderButton(Graphics g, string key, RectangleF r, Action<Graphics, RectangleF, Color> glyph, Color hover)
        {
            bool hot = HoverKey == key;
            using (var b = new SolidBrush(hot ? hover.WithAlpha(key == "close" ? 220 : 255) : Theme.Card))
                g.FillRounded(b, r, 10);
            using (var p = new Pen(hot ? hover : Theme.Border))
                g.DrawRounded(p, r, 10);
            glyph(g, r, hot ? Color.White : Theme.Muted);
            HitAreas[key] = r;
        }

        private static void DrawCloseGlyph(Graphics g, RectangleF r, Color c)
        {
            using (var p = new Pen(c, 1.8f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
            {
                float cx = r.X + r.Width / 2, cy = r.Y + r.Height / 2, s = 5.5f;
                g.DrawLine(p, cx - s, cy - s, cx + s, cy + s);
                g.DrawLine(p, cx - s, cy + s, cx + s, cy - s);
            }
        }

        private static void DrawMinGlyph(Graphics g, RectangleF r, Color c)
        {
            using (var p = new Pen(c, 1.8f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
            {
                float cx = r.X + r.Width / 2, cy = r.Y + r.Height / 2;
                g.DrawLine(p, cx - 6, cy, cx + 6, cy);
            }
        }

        private static void DrawGearGlyph(Graphics g, RectangleF r, Color c)
        {
            float cx = r.X + r.Width / 2, cy = r.Y + r.Height / 2;
            using (var p = new Pen(c, 1.7f))
            using (var path = new GraphicsPath())
            {
                const int teeth = 8;
                for (int i = 0; i < teeth * 2; i++)
                {
                    double a0 = Math.PI * 2 * i / (teeth * 2);
                    double a1 = Math.PI * 2 * (i + 1) / (teeth * 2);
                    float rad = i % 2 == 0 ? 8.2f : 6.4f;
                    path.AddLine(cx + rad * (float)Math.Cos(a0), cy + rad * (float)Math.Sin(a0), cx + rad * (float)Math.Cos(a1), cy + rad * (float)Math.Sin(a1));
                }
                path.CloseFigure();
                g.DrawPath(p, path);
                g.DrawEllipse(p, cx - 2.8f, cy - 2.8f, 5.6f, 5.6f);
            }
        }

        private static void DrawUsbGlyph(Graphics g, RectangleF r, Color c)
        {
            using (var p = new Pen(c, 1.7f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
            {
                float cx = r.X + r.Width / 2;
                g.DrawLine(p, cx, r.Y + 3, cx, r.Bottom - 4);
                g.DrawLine(p, cx, r.Y + 11, cx - 5, r.Y + 7);
                g.DrawLine(p, cx, r.Y + 13, cx + 5, r.Y + 9);
                g.DrawEllipse(p, cx - 7, r.Y + 5, 3, 3);
                g.DrawRectangle(p, cx + 4, r.Y + 7, 3, 3);
                using (var b = new SolidBrush(c))
                    g.FillEllipse(b, cx - 2.6f, r.Bottom - 6, 5.2f, 5.2f);
            }
        }

        // CPU / GPU panels --------------------------------------------------------------------

        private void PaintChipPanel(Graphics g, RectangleF r, string title, string model, Color accent,
            MetricKind temp, MetricKind load, MetricKind power, MetricKind clock, MetricKind extra)
        {
            Card(g, r);

            // Title row
            var chip = M(new RectangleF(Unmirror(r).X + 20, r.Y + 18, 44, 30));
            using (var b = new SolidBrush(accent.WithAlpha(36))) g.FillRounded(b, chip, 9);
            using (var p = new Pen(accent.WithAlpha(150))) g.DrawRounded(p, chip, 9);
            using (var f = LatinFont(12.5f, FontStyle.Bold))
                Text(g, title, f, accent, chip, Align.Center, Align.Center, false);

            var lr = Unmirror(r);
            using (var f = LatinFont(14.5f, FontStyle.Bold))
                Text(g, string.IsNullOrEmpty(model) ? title : model, f, Theme.Text,
                    M(lr.X + 76, r.Y + 18, lr.Width - 96, 30), Align.Start, Align.Center, false);

            // Gauge
            var gaugeCenter = M(new PointF(lr.X + 135, r.Y + 190));
            PaintTempGauge(g, gaugeCenter, 98, temp);

            // Tiles
            float tx = lr.X + 268, ty = r.Y + 66;
            float tw = (lr.Right - 18 - tx - 12) / 2f, th = (r.Bottom - 18 - ty - 12) / 2f;
            var tiles = new[]
            {
                (load, Strings.Get("Usage"), M(tx, ty, tw, th)),
                (power, Strings.Get("Power"), M(tx + tw + 12, ty, tw, th)),
                (clock, Strings.Get("Clock"), M(tx, ty + th + 12, tw, th)),
                (extra, extra == MetricKind.CpuVoltage ? Strings.Get("Voltage") : Strings.Get("VRAM"), M(tx + tw + 12, ty + th + 12, tw, th)),
            };
            foreach (var t in tiles)
                PaintTile(g, t.Item3, t.Item2, t.Item1, accent);
        }

        private static RectangleF Unmirror(RectangleF r) => M(r); // M is its own inverse

        private void PaintTempGauge(Graphics g, PointF c, float radius, MetricKind kind)
        {
            const float start = 135f, sweep = 270f, thick = 15f;
            var rect = new RectangleF(c.X - radius, c.Y - radius, radius * 2, radius * 2);
            bool has = Has(kind);
            double celsius = Get(kind);
            double frac = has ? Math.Max(0, Math.Min(1, (celsius - 20) / 80.0)) : 0;
            var col = Theme.TempColor(celsius);

            // tick marks
            using (var p = new Pen(Theme.Faint, 1.4f))
            {
                for (int i = 0; i <= 40; i++)
                {
                    double a = (start + sweep * i / 40.0) * Math.PI / 180;
                    float r1 = radius + 13, r2 = radius + (i % 5 == 0 ? 19 : 16);
                    g.DrawLine(p, c.X + r1 * (float)Math.Cos(a), c.Y + r1 * (float)Math.Sin(a), c.X + r2 * (float)Math.Cos(a), c.Y + r2 * (float)Math.Sin(a));
                }
            }

            using (var p = new Pen(Theme.Border, thick) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                g.DrawArc(p, rect, start, sweep);

            if (has && frac > 0.002)
            {
                float sw = (float)(sweep * frac);
                using (var p = new Pen(col.WithAlpha(45), thick + 12) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                    g.DrawArc(p, rect, start, sw);
                using (var b = new LinearGradientBrush(RectangleF.Inflate(rect, 10, 10), Theme.Cyan, col, LinearGradientMode.ForwardDiagonal))
                using (var p = new Pen(b, thick) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                    g.DrawArc(p, rect, start, sw);
                // knob
                double a = (start + sw) * Math.PI / 180;
                var k = new PointF(c.X + radius * (float)Math.Cos(a), c.Y + radius * (float)Math.Sin(a));
                using (var b = new SolidBrush(Color.White)) g.FillEllipse(b, k.X - 4.5f, k.Y - 4.5f, 9, 9);
            }

            string value = has ? Math.Round(T(celsius)).ToString("0", CultureInfo.InvariantCulture) : "--";
            using (var fBig = NumFont(52, FontStyle.Bold))
            using (var fUnit = NumFont(20))
            using (var fLbl = UiFont(13f))
            using (var fMax = UiFont(11.5f))
            {
                var vs = Measure(g, value, fBig);
                var us = Measure(g, TempUnit, fUnit);
                float total = vs.Width + us.Width + 2;
                float left = c.X - total / 2;
                using (var sf = new StringFormat(StringFormat.GenericTypographic))
                using (var bv = new SolidBrush(has ? Theme.Text : Theme.Faint))
                using (var b = new SolidBrush(col))
                {
                    g.DrawString(value, fBig, bv, left, c.Y - 46, sf);
                    g.DrawString(TempUnit, fUnit, b, left + vs.Width + 2, c.Y - 36, sf);
                }

                Text(g, Strings.Get("Temperature"), fLbl, Theme.Muted, new RectangleF(c.X - 80, c.Y + 14, 160, 20), Align.Center);
                if (SessionMax.TryGetValue(kind, out var mx))
                {
                    string max = "MAX " + Math.Round(T(mx)).ToString("0", CultureInfo.InvariantCulture) + "°";
                    var pill = new RectangleF(c.X - 38, c.Y + 42, 76, 22);
                    using (var b = new SolidBrush(Theme.Background.WithAlpha(160))) g.FillRounded(b, pill, 11);
                    using (var f = LatinFont(11, FontStyle.Bold))
                        Text(g, max, f, Theme.Muted, pill, Align.Center, Align.Center, false);
                }
            }
        }

        private void PaintTile(Graphics g, RectangleF r, string label, MetricKind kind, Color accent)
        {
            using (var b = new SolidBrush(Theme.Background.WithAlpha(120))) g.FillRounded(b, r, 14);
            using (var p = new Pen(Theme.Border.WithAlpha(200))) g.DrawRounded(p, r, 14);

            var lr = Unmirror(r);
            using (var f = UiFont(12.5f))
                Text(g, label, f, Theme.Muted, M(lr.X + 14, r.Y + 10, lr.Width - 28, 18));

            FormatValue(kind, out string value, out string unit);
            using (var fv = NumFont(27, FontStyle.Bold))
            using (var fu = NumFont(13.5f))
            {
                var vs = Measure(g, value, fv);
                var us = Measure(g, unit, fu);
                float y = r.Y + 30;
                float startX = Rtl ? r.Right - 14 - vs.Width - us.Width - 4 : r.X + 14;
                using (var sf = new StringFormat(StringFormat.GenericTypographic))
                using (var bv = new SolidBrush(Has(kind) ? Theme.Text : Theme.Faint))
                using (var bu = new SolidBrush(Theme.Muted))
                {
                    g.DrawString(value, fv, bv, startX, y, sf);
                    g.DrawString(unit, fu, bu, startX + vs.Width + 4, y + 12, sf);
                }
            }

            var spark = new RectangleF(r.X + 14, r.Bottom - 34, r.Width - 28, 22);
            if (kind == MetricKind.CpuLoad || kind == MetricKind.GpuLoad || kind == MetricKind.GpuMemLoad)
            {
                var bar = new RectangleF(r.X + 14, r.Bottom - 20, r.Width - 28, 7);
                using (var b = new SolidBrush(Theme.Border)) g.FillRounded(b, bar, 3.5f);
                double pct = Math.Max(0, Math.Min(100, Get(kind))) / 100.0;
                if (pct > 0.01)
                {
                    float w = (float)(bar.Width * pct);
                    var fill = Rtl ? new RectangleF(bar.Right - w, bar.Y, w, bar.Height) : new RectangleF(bar.X, bar.Y, w, bar.Height);
                    using (var b = new LinearGradientBrush(bar, Rtl ? Theme.Pink : accent, Rtl ? accent : Theme.Pink, LinearGradientMode.Horizontal))
                        g.FillRounded(b, fill, 3.5f);
                }
                spark = new RectangleF(spark.X, spark.Y - 8, spark.Width, 16);
                Sparkline(g, spark, History[kind], accent.WithAlpha(150), false);
            }
            else
            {
                Sparkline(g, spark, History[kind], accent, true);
            }
        }

        private void FormatValue(MetricKind k, out string value, out string unit)
        {
            unit = "";
            if (!Has(k))
            {
                value = "--";
                return;
            }
            double v = Get(k);
            var inv = CultureInfo.InvariantCulture;
            switch (k)
            {
                case MetricKind.CpuLoad:
                case MetricKind.GpuLoad:
                case MetricKind.GpuMemLoad:
                case MetricKind.RamLoad:
                    value = Math.Round(v).ToString("0", inv); unit = "%"; break;
                case MetricKind.CpuPower:
                case MetricKind.GpuPower:
                    value = v.ToString(v >= 100 ? "0" : "0.0", inv); unit = "W"; break;
                case MetricKind.CpuClock:
                case MetricKind.GpuClock:
                    if (v >= 1000) { value = (v / 1000).ToString("0.00", inv); unit = "GHz"; }
                    else { value = v.ToString("0", inv); unit = "MHz"; }
                    break;
                case MetricKind.CpuVoltage:
                    value = v.ToString("0.000", inv); unit = "V"; break;
                case MetricKind.Fan1:
                case MetricKind.Fan2:
                    value = Math.Round(v).ToString("0", inv); unit = "RPM"; break;
                default:
                    value = v.ToString("0", inv); break;
            }
        }

        private void Sparkline(Graphics g, RectangleF r, List<double> data, Color c, bool fill)
        {
            if (data.Count < 2)
                return;
            int n = Math.Min(data.Count, 60);
            var pts = data.Skip(data.Count - n).ToList();
            double min = pts.Min(), max = pts.Max();
            if (max - min < 1e-6) { max += 1; min -= 1; }
            var poly = new PointF[n];
            for (int i = 0; i < n; i++)
            {
                float x = r.X + r.Width * i / (60 - 1);
                if (Rtl) x = r.Right - (x - r.X);
                float y = r.Bottom - (float)((pts[i] - min) / (max - min)) * r.Height;
                poly[i] = new PointF(x, y);
            }
            if (fill)
            {
                using (var path = new GraphicsPath())
                {
                    path.AddLines(poly);
                    path.AddLine(poly[n - 1].X, poly[n - 1].Y, poly[n - 1].X, r.Bottom + 2);
                    path.AddLine(poly[n - 1].X, r.Bottom + 2, poly[0].X, r.Bottom + 2);
                    path.CloseFigure();
                    using (var b = new LinearGradientBrush(new RectangleF(r.X, r.Y - 1, r.Width, r.Height + 4), c.WithAlpha(70), c.WithAlpha(0), LinearGradientMode.Vertical))
                        g.FillPath(b, path);
                }
            }
            using (var p = new Pen(c, 1.6f) { LineJoin = LineJoin.Round })
                g.DrawCurve(p, poly, 0.4f);
        }

        // History --------------------------------------------------------------------------------

        private void PaintHistory(Graphics g, RectangleF r)
        {
            Card(g, r);
            var lr = Unmirror(r);
            using (var f = UiFont(15f, FontStyle.Bold))
                Text(g, Strings.Get("History"), f, Theme.Text, M(lr.X + 20, r.Y + 16, 260, 24));
            using (var f = UiFont(11.5f))
                Text(g, Strings.Get("Last2Min"), f, Theme.Muted, M(lr.X + 20, r.Y + 40, 260, 16));

            // legend
            float lx = lr.Right - 20 - 170;
            Legend(g, M(lx, r.Y + 20, 80, 18), "CPU", Theme.Cyan);
            Legend(g, M(lx + 90, r.Y + 20, 80, 18), "GPU", Theme.Violet);

            var plot = new RectangleF(r.X + 52, r.Y + 72, r.Width - 76, r.Height - 100);
            if (Rtl) plot = new RectangleF(r.X + 24, plot.Y, plot.Width, plot.Height);

            double lo = 20, hi = 100; // °C
            using (var p = new Pen(Theme.Border) { DashStyle = DashStyle.Dash })
            using (var f = LatinFont(11))
            {
                for (int i = 0; i <= 4; i++)
                {
                    double c = lo + (hi - lo) * i / 4.0;
                    float y = plot.Bottom - plot.Height * i / 4f;
                    g.DrawLine(p, plot.X, y, plot.Right, y);
                    string lbl = Math.Round(T(c)).ToString("0", CultureInfo.InvariantCulture) + "°";
                    var lblRect = Rtl ? new RectangleF(plot.Right + 4, y - 8, 40, 16) : new RectangleF(plot.X - 44, y - 8, 38, 16);
                    using (var sf = new StringFormat { Alignment = Rtl ? StringAlignment.Near : StringAlignment.Far, LineAlignment = StringAlignment.Center })
                    using (var b = new SolidBrush(Theme.Faint))
                        g.DrawString(lbl, f, b, lblRect, sf);
                }
            }

            HistoryLine(g, plot, History[MetricKind.GpuTemp], lo, hi, Theme.Violet);
            HistoryLine(g, plot, History[MetricKind.CpuTemp], lo, hi, Theme.Cyan);
        }

        private void Legend(Graphics g, RectangleF r, string text, Color c)
        {
            var dot = Rtl ? new RectangleF(r.Right - 12, r.Y + 5, 10, 10) : new RectangleF(r.X, r.Y + 5, 10, 10);
            using (var f = LatinFont(12, FontStyle.Bold))
            {
                var tr = Rtl ? new RectangleF(r.X, r.Y, r.Width - 18, r.Height) : new RectangleF(r.X + 18, r.Y, r.Width - 18, r.Height);
                string val = "";
                var k = text == "CPU" ? MetricKind.CpuTemp : MetricKind.GpuTemp;
                if (Has(k)) val = "  " + Math.Round(T(Get(k))).ToString("0", CultureInfo.InvariantCulture) + "°";
                Text(g, text + val, f, Theme.Muted, tr, Align.Start, Align.Center, false);
            }
            using (var b = new SolidBrush(c)) g.FillRounded(b, dot, 3);
        }

        private void HistoryLine(Graphics g, RectangleF plot, List<double> data, double lo, double hi, Color c)
        {
            if (data.Count < 2)
                return;
            var pts = new PointF[data.Count];
            for (int i = 0; i < data.Count; i++)
            {
                float x = plot.X + plot.Width * (HistoryLength - data.Count + i) / (HistoryLength - 1);
                if (Rtl) x = plot.Right - (x - plot.X);
                double v = Math.Max(lo, Math.Min(hi, data[i]));
                float y = plot.Bottom - (float)((v - lo) / (hi - lo)) * plot.Height;
                pts[i] = new PointF(x, y);
            }
            using (var path = new GraphicsPath())
            {
                path.AddCurve(pts, 0.35f);
                path.AddLine(pts[pts.Length - 1].X, pts[pts.Length - 1].Y, pts[pts.Length - 1].X, plot.Bottom);
                path.AddLine(pts[pts.Length - 1].X, plot.Bottom, pts[0].X, plot.Bottom);
                path.CloseFigure();
                using (var b = new LinearGradientBrush(plot, c.WithAlpha(60), c.WithAlpha(0), LinearGradientMode.Vertical))
                    g.FillPath(b, path);
            }
            using (var p = new Pen(c.WithAlpha(60), 6f) { LineJoin = LineJoin.Round })
                g.DrawCurve(p, pts, 0.35f);
            using (var p = new Pen(c, 2.2f) { LineJoin = LineJoin.Round })
                g.DrawCurve(p, pts, 0.35f);
            var last = pts[pts.Length - 1];
            using (var b = new SolidBrush(c.WithAlpha(70))) g.FillEllipse(b, last.X - 7, last.Y - 7, 14, 14);
            using (var b = new SolidBrush(Color.White)) g.FillEllipse(b, last.X - 3.5f, last.Y - 3.5f, 7, 7);
        }

        // Fans -----------------------------------------------------------------------------------

        private void PaintFans(Graphics g, RectangleF r)
        {
            Card(g, r);
            var lr = Unmirror(r);
            using (var f = UiFont(15f, FontStyle.Bold))
                Text(g, Strings.Get("Fans"), f, Theme.Text, M(lr.X + 20, r.Y + 16, 200, 24));

            FanRow(g, M(lr.X + 14, r.Y + 58, lr.Width - 28, 104), Strings.Get("Fan1"), MetricKind.Fan1, Fan1Angle, Theme.Cyan);
            FanRow(g, M(lr.X + 14, r.Y + 172, lr.Width - 28, 104), Strings.Get("Fan2"), MetricKind.Fan2, Fan2Angle, Theme.Violet);
        }

        private void FanRow(Graphics g, RectangleF r, string label, MetricKind kind, float angle, Color accent)
        {
            using (var b = new SolidBrush(Theme.Background.WithAlpha(120))) g.FillRounded(b, r, 14);
            using (var p = new Pen(Theme.Border.WithAlpha(200))) g.DrawRounded(p, r, 14);

            var lr = Unmirror(r);
            var c = M(new PointF(lr.X + 52, r.Y + r.Height / 2));
            DrawFan(g, c, 34, Has(kind) ? angle : 0, Has(kind) && Get(kind) > 1 ? accent : Theme.Faint);

            using (var f = UiFont(12.5f))
                Text(g, label, f, Theme.Muted, M(lr.X + 100, r.Y + 18, lr.Width - 110, 18));
            FormatValue(kind, out string value, out string unit);
            using (var fv = NumFont(28, FontStyle.Bold))
            using (var fu = NumFont(13))
            {
                var vs = Measure(g, value, fv);
                var us = Measure(g, unit, fu);
                float x = Rtl ? lr.X + 0 : lr.X + 100;
                if (Rtl) x = r.Right - 100 - vs.Width - us.Width - 4;
                float y = r.Y + 42;
                using (var sf = new StringFormat(StringFormat.GenericTypographic))
                using (var bv = new SolidBrush(Has(kind) ? Theme.Text : Theme.Faint))
                using (var bu = new SolidBrush(Theme.Muted))
                {
                    g.DrawString(value, fv, bv, x, y, sf);
                    g.DrawString(unit, fu, bu, x + vs.Width + 4, y + 13, sf);
                }
            }
        }

        private static void DrawFan(Graphics g, PointF c, float radius, float angle, Color color)
        {
            using (var p = new Pen(color.WithAlpha(90), 2.5f))
                g.DrawEllipse(p, c.X - radius, c.Y - radius, radius * 2, radius * 2);

            var state = g.Save();
            g.TranslateTransform(c.X, c.Y);
            g.RotateTransform(angle);
            using (var b = new SolidBrush(color.WithAlpha(210)))
            {
                for (int i = 0; i < 5; i++)
                {
                    using (var blade = new GraphicsPath())
                    {
                        blade.AddBezier(0, -6, radius * 0.45f, -radius * 0.95f, radius * 0.95f, -radius * 0.55f, radius * 0.72f, -radius * 0.08f);
                        blade.AddBezier(radius * 0.72f, -radius * 0.08f, radius * 0.45f, -radius * 0.25f, radius * 0.2f, -radius * 0.25f, 0, 6);
                        blade.CloseFigure();
                        g.FillPath(b, blade);
                    }
                    g.RotateTransform(72);
                }
            }
            g.Restore(state);
            using (var b = new SolidBrush(Theme.Card)) g.FillEllipse(b, c.X - 9, c.Y - 9, 18, 18);
            using (var p = new Pen(color, 2f)) g.DrawEllipse(p, c.X - 9, c.Y - 9, 18, 18);
        }

        // RAM ------------------------------------------------------------------------------------

        private void PaintRam(Graphics g, RectangleF r)
        {
            Card(g, r);
            var lr = Unmirror(r);
            using (var f = UiFont(15f, FontStyle.Bold))
                Text(g, Strings.Get("Ram"), f, Theme.Text, M(lr.X + 20, r.Y + 16, 200, 24));

            var c = new PointF(r.X + r.Width / 2, r.Y + 146);
            float rad = 70;
            var rect = new RectangleF(c.X - rad, c.Y - rad, rad * 2, rad * 2);
            double pct = Math.Max(0, Math.Min(100, Get(MetricKind.RamLoad)));
            using (var p = new Pen(Theme.Border, 13f)) g.DrawEllipse(p, rect);
            if (Has(MetricKind.RamLoad) && pct > 0.5)
            {
                float sw = (float)(360 * pct / 100.0) * (Rtl ? -1 : 1);
                using (var p = new Pen(Theme.Pink.WithAlpha(40), 24f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                    g.DrawArc(p, rect, -90, sw);
                using (var b = new LinearGradientBrush(RectangleF.Inflate(rect, 8, 8), Theme.Violet, Theme.Pink, LinearGradientMode.ForwardDiagonal))
                using (var p = new Pen(b, 13f) { StartCap = LineCap.Round, EndCap = LineCap.Round })
                    g.DrawArc(p, rect, -90, sw);
            }
            FormatValue(MetricKind.RamLoad, out string value, out string unit);
            using (var fv = NumFont(38, FontStyle.Bold))
            using (var fu = NumFont(16))
            {
                var vs = Measure(g, value, fv);
                var us = Measure(g, unit, fu);
                float left = c.X - (vs.Width + us.Width + 2) / 2;
                using (var sf = new StringFormat(StringFormat.GenericTypographic))
                using (var bv = new SolidBrush(Theme.Text))
                using (var bu = new SolidBrush(Theme.Pink))
                {
                    g.DrawString(value, fv, bv, left, c.Y - vs.Height / 2 - 2, sf);
                    g.DrawString(unit, fu, bu, left + vs.Width + 2, c.Y - vs.Height / 2 + 14, sf);
                }
            }
            if (Target.RamTotalGb > 0)
            {
                using (var f = UiFont(12.5f))
                    Text(g, string.Format(CultureInfo.InvariantCulture, Strings.Get("RamUsed"), Target.RamUsedGb, Target.RamTotalGb),
                        f, Theme.Muted, new RectangleF(r.X + 10, r.Bottom - 50, r.Width - 20, 20), Align.Center);
            }
        }

        // Footer ---------------------------------------------------------------------------------

        private void PaintFooter(Graphics g)
        {
            using (var f = UiFont(12f))
            using (var fb = UiFont(12f, FontStyle.Bold))
            {
                Color dot = SensorsReady ? Theme.Green : Theme.Amber;
                var d = M(22, 732, 8, 8);
                using (var b = new SolidBrush(dot)) g.FillEllipse(b, d);
                string status = SensorsReady
                    ? Strings.Get("Ready") + (LastUpdate > DateTime.MinValue ? "  ·  " + LastUpdate.ToString("HH:mm:ss", CultureInfo.InvariantCulture) : "")
                    : Strings.Get("Loading");
                if (Demo) status += "  ·  DEMO";
                Text(g, status, f, Theme.Muted, M(38, 724, 360, 24));

                if (!string.IsNullOrEmpty(Warning))
                {
                    string msg = Strings.Get("PawnIO");
                    string link = Strings.Get("Install");
                    float mw = Measure(g, msg, f).Width;
                    float lw = Measure(g, link, fb).Width + 24;
                    float total = mw + 12 + lw + 30;
                    float x0 = W / 2 - total / 2;
                    var warnBox = M(x0, 722, total, 28);
                    using (var b = new SolidBrush(Theme.Amber.WithAlpha(28))) g.FillRounded(b, warnBox, 14);
                    using (var p = new Pen(Theme.Amber.WithAlpha(110))) g.DrawRounded(p, warnBox, 14);
                    var ic = M(x0 + 10, 729, 14, 14);
                    using (var b = new SolidBrush(Theme.Amber)) g.FillEllipse(b, ic);
                    using (var fi = LatinFont(11, FontStyle.Bold))
                        Text(g, "!", fi, Theme.Background, ic, Align.Center, Align.Center, false);
                    Text(g, msg, f, Theme.Amber, M(x0 + 30, 722, mw + 6, 28));
                    var lb = M(x0 + 30 + mw + 12, 725, lw, 22);
                    bool hot = HoverKey == "install";
                    using (var b = new SolidBrush(hot ? Theme.Amber : Theme.Amber.WithAlpha(200))) g.FillRounded(b, lb, 11);
                    Text(g, link, fb, Theme.Background, lb, Align.Center);
                    HitAreas["install"] = lb;
                }

                using (var fl = LatinFont(12f))
                    Text(g, "v" + Version, fl, Theme.Faint, M(W - 20 - 120, 724, 120, 24), Align.End, Align.Center, false);
            }
        }
    }
}
