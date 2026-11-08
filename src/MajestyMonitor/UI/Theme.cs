using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Text;
using System.Linq;

namespace MajestyMonitor.UI
{
    internal static class Theme
    {
        public static readonly Color Background = Color.FromArgb(9, 12, 22);
        public static readonly Color Card = Color.FromArgb(17, 22, 38);
        public static readonly Color CardHi = Color.FromArgb(23, 30, 50);
        public static readonly Color Border = Color.FromArgb(34, 43, 68);
        public static readonly Color Text = Color.FromArgb(232, 236, 247);
        public static readonly Color Muted = Color.FromArgb(132, 142, 171);
        public static readonly Color Faint = Color.FromArgb(70, 80, 108);
        public static readonly Color Cyan = Color.FromArgb(34, 211, 238);
        public static readonly Color Violet = Color.FromArgb(139, 92, 246);
        public static readonly Color Green = Color.FromArgb(34, 197, 94);
        public static readonly Color Amber = Color.FromArgb(245, 158, 11);
        public static readonly Color Red = Color.FromArgb(239, 68, 68);
        public static readonly Color Pink = Color.FromArgb(236, 72, 153);

        private static string _uiFamily;
        private static string _numFamily;
        private static string _arFamily;

        public static string UiFamily => _uiFamily ?? (_uiFamily = Pick("Segoe UI", "Noto Sans", "DejaVu Sans", "Tahoma", "Arial"));
        public static string NumFamily => _numFamily ?? (_numFamily = Pick("Bahnschrift", "Segoe UI Semibold", "Segoe UI", "Noto Sans", "DejaVu Sans", "Arial"));
        public static string ArabicFamily => _arFamily ?? (_arFamily = Pick("Noto Sans Arabic UI", "Noto Sans Arabic", "Segoe UI", "Tahoma", "Arial"));

        private static string Pick(params string[] names)
        {
            try
            {
                using (var fonts = new InstalledFontCollection())
                {
                    var installed = fonts.Families.Select(f => f.Name).ToList();
                    foreach (var n in names)
                        if (installed.Any(i => string.Equals(i, n, StringComparison.OrdinalIgnoreCase)))
                            return n;
                }
            }
            catch { }
            return FontFamily.GenericSansSerif.Name;
        }

        public static Color Lerp(Color a, Color b, double t)
        {
            t = Math.Max(0, Math.Min(1, t));
            return Color.FromArgb(
                (int)(a.A + (b.A - a.A) * t),
                (int)(a.R + (b.R - a.R) * t),
                (int)(a.G + (b.G - a.G) * t),
                (int)(a.B + (b.B - a.B) * t));
        }

        public static Color WithAlpha(this Color c, int a) => Color.FromArgb(Math.Max(0, Math.Min(255, a)), c.R, c.G, c.B);

        /// <summary>Colour of a temperature in °C: cool cyan, warm amber, hot red.</summary>
        public static Color TempColor(double c)
        {
            if (c <= 45) return Cyan;
            if (c <= 65) return Lerp(Cyan, Green, (c - 45) / 20.0);
            if (c <= 80) return Lerp(Green, Amber, (c - 65) / 15.0);
            return Lerp(Amber, Red, (c - 80) / 12.0);
        }

        public static GraphicsPath Rounded(RectangleF r, float radius)
        {
            var p = new GraphicsPath();
            float d = Math.Min(radius * 2, Math.Min(r.Width, r.Height));
            if (d <= 0.5f)
            {
                p.AddRectangle(r);
                return p;
            }
            p.AddArc(r.X, r.Y, d, d, 180, 90);
            p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
            p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
            p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
            p.CloseFigure();
            return p;
        }

        public static void FillRounded(this Graphics g, Brush b, RectangleF r, float radius)
        {
            using (var p = Rounded(r, radius))
                g.FillPath(b, p);
        }

        public static void DrawRounded(this Graphics g, Pen pen, RectangleF r, float radius)
        {
            using (var p = Rounded(r, radius))
                g.DrawPath(pen, p);
        }

        public static void Setup(Graphics g)
        {
            g.SmoothingMode = SmoothingMode.AntiAlias;
            g.PixelOffsetMode = PixelOffsetMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;
            g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
            g.CompositingQuality = CompositingQuality.HighQuality;
        }
    }
}
