using System;
using System.Collections.Generic;
using System.Drawing;
using System.Globalization;
using System.Linq;
using System.Windows.Forms;
using MajestyMonitor.Core;

namespace MajestyMonitor.UI
{
    /// <summary>Dark settings dialog: general options plus a sensor picker for every metric.</summary>
    internal sealed class SettingsForm : Form
    {
        private readonly AppSettings _settings;
        private readonly ISensorSource _source;

        private readonly ComboBox _lang = NewCombo();
        private readonly ComboBox _unit = NewCombo();
        private readonly ComboBox _refresh = NewCombo();
        private readonly CheckBox _autoStart = NewCheck();
        private readonly CheckBox _startMin = NewCheck();
        private readonly CheckBox _closeTray = NewCheck();
        private readonly CheckBox _send = NewCheck();
        private readonly Dictionary<MetricKind, ComboBox> _sensorCombos = new Dictionary<MetricKind, ComboBox>();

        private static readonly int[] RefreshValues = { 500, 1000, 2000 };

        public SettingsForm(AppSettings settings, ISensorSource source)
        {
            _settings = settings;
            _source = source;

            Text = "MAJESTY · " + Strings.Get("Settings");
            Icon = Program.AppIcon;
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96f, 96f);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ShowInTaskbar = false;
            StartPosition = FormStartPosition.CenterParent;
            BackColor = Theme.Background;
            ForeColor = Theme.Text;
            Font = new Font(Strings.Rtl ? Theme.ArabicFamily : Theme.UiFamily, 9.75f);
            ClientSize = new Size(940, 640);
            if (Strings.Rtl)
            {
                RightToLeft = RightToLeft.Yes;
                RightToLeftLayout = true;
            }

            BuildGeneral();
            BuildSensors();
            BuildButtons();
            LoadValues();
        }

        // ---- Controls ---------------------------------------------------------------------

        private static ComboBox NewCombo() => new ComboBox
        {
            DropDownStyle = ComboBoxStyle.DropDownList,
            FlatStyle = FlatStyle.Flat,
            BackColor = Theme.Card,
            ForeColor = Theme.Text,
        };

        private static CheckBox NewCheck() => new CheckBox
        {
            AutoSize = true,
            ForeColor = Theme.Text,
            FlatStyle = FlatStyle.Flat,
        };

        private Panel Section(string title, Rectangle bounds)
        {
            var panel = new Panel { Bounds = bounds, BackColor = Theme.Card };
            panel.Paint += (s, e) =>
            {
                using (var p = new Pen(Theme.Border))
                    e.Graphics.DrawRectangle(p, 0, 0, panel.Width - 1, panel.Height - 1);
            };
            var lbl = new Label
            {
                Text = title,
                AutoSize = false,
                Bounds = new Rectangle(16, 12, bounds.Width - 32, 26),
                ForeColor = Theme.Cyan,
                Font = new Font(Font.FontFamily, 11.5f, FontStyle.Bold),
                TextAlign = Strings.Rtl ? ContentAlignment.MiddleRight : ContentAlignment.MiddleLeft,
            };
            panel.Controls.Add(lbl);
            Controls.Add(panel);
            return panel;
        }

        private Label FieldLabel(Control parent, string text, int y, int width)
        {
            var l = new Label
            {
                Text = text,
                AutoSize = false,
                Bounds = new Rectangle(16, y, width, 22),
                ForeColor = Theme.Muted,
                TextAlign = Strings.Rtl ? ContentAlignment.MiddleRight : ContentAlignment.MiddleLeft,
            };
            parent.Controls.Add(l);
            return l;
        }

        private void BuildGeneral()
        {
            var p = Section(Strings.Get("General"), new Rectangle(16, 16, 330, 560));
            int y = 52;

            FieldLabel(p, Strings.Get("Language"), y, 298);
            _lang.Bounds = new Rectangle(16, y + 24, 298, 28);
            _lang.Items.AddRange(new object[] { "العربية", "English" });
            p.Controls.Add(_lang);
            y += 66;

            FieldLabel(p, Strings.Get("Units"), y, 298);
            _unit.Bounds = new Rectangle(16, y + 24, 298, 28);
            _unit.Items.AddRange(new object[] { "Celsius  °C", "Fahrenheit  °F" });
            p.Controls.Add(_unit);
            y += 66;

            FieldLabel(p, Strings.Get("Refresh"), y, 298);
            _refresh.Bounds = new Rectangle(16, y + 24, 298, 28);
            _refresh.Items.AddRange(RefreshValues.Select(v => (v / 1000.0).ToString("0.0", CultureInfo.InvariantCulture) + " s").Cast<object>().ToArray());
            p.Controls.Add(_refresh);
            y += 80;

            foreach (var (cb, key) in new[] { (_send, "SendDevice"), (_autoStart, "AutoStart"), (_startMin, "StartMin"), (_closeTray, "CloseTray") })
            {
                cb.Text = Strings.Get(key);
                cb.Location = new Point(16, y);
                cb.MaximumSize = new Size(298, 0);
                p.Controls.Add(cb);
                y += 36;
            }
        }

        private void BuildSensors()
        {
            var p = Section(Strings.Get("Sensors"), new Rectangle(362, 16, 562, 560));
            int y = 50;
            foreach (var kind in MetricInfo.Selectable)
            {
                var l = FieldLabel(p, Strings.Metric(kind), y + 3, 150);
                var cb = NewCombo();
                cb.Bounds = new Rectangle(176, y, 370, 28);
                cb.DropDownWidth = 480;
                p.Controls.Add(cb);
                _sensorCombos[kind] = cb;
                y += 38;
            }

            var reset = FlatButton(Strings.Get("Reset"), false);
            reset.Bounds = new Rectangle(16, 560 - 46, 180, 32);
            reset.Click += (s, e) =>
            {
                foreach (var cb in _sensorCombos.Values)
                    if (cb.Items.Count > 0) cb.SelectedIndex = 0;
            };
            p.Controls.Add(reset);
        }

        private Button FlatButton(string text, bool primary)
        {
            var b = new Button
            {
                Text = text,
                FlatStyle = FlatStyle.Flat,
                BackColor = primary ? Theme.Cyan : Theme.CardHi,
                ForeColor = primary ? Theme.Background : Theme.Text,
                Font = new Font(Font.FontFamily, 10f, primary ? FontStyle.Bold : FontStyle.Regular),
                Cursor = Cursors.Hand,
            };
            b.FlatAppearance.BorderColor = primary ? Theme.Cyan : Theme.Border;
            b.FlatAppearance.MouseOverBackColor = primary ? Theme.Lerp(Theme.Cyan, Color.White, 0.2) : Theme.Border;
            return b;
        }

        private void BuildButtons()
        {
            var save = FlatButton(Strings.Get("Save"), true);
            save.Bounds = new Rectangle(ClientSize.Width - 16 - 130, ClientSize.Height - 50, 130, 36);
            save.Click += (s, e) => { SaveValues(); DialogResult = DialogResult.OK; Close(); };
            var cancel = FlatButton(Strings.Get("Cancel"), false);
            cancel.Bounds = new Rectangle(ClientSize.Width - 16 - 130 - 12 - 120, ClientSize.Height - 50, 120, 36);
            cancel.DialogResult = DialogResult.Cancel;
            Controls.Add(save);
            Controls.Add(cancel);
            AcceptButton = save;
            CancelButton = cancel;
        }

        // ---- Values -----------------------------------------------------------------------

        private sealed class Item
        {
            public string Id;
            public string Text;
            public override string ToString() => Text;
        }

        private void LoadValues()
        {
            _lang.SelectedIndex = _settings.Language == "en" ? 1 : 0;
            _unit.SelectedIndex = _settings.Fahrenheit ? 1 : 0;
            int ri = Array.IndexOf(RefreshValues, _settings.RefreshMs);
            _refresh.SelectedIndex = ri >= 0 ? ri : 1;
            _send.Checked = _settings.SendToDevice;
            _autoStart.Checked = _settings.AutoStart;
            _startMin.Checked = _settings.StartMinimized;
            _closeTray.Checked = _settings.CloseToTray;

            foreach (var kv in _sensorCombos)
            {
                var cb = kv.Value;
                cb.Items.Clear();
                cb.Items.Add(new Item { Id = "", Text = Strings.Get("Auto") });
                IList<SensorOption> options;
                try { options = _source.GetOptions(kv.Key); } catch { options = new List<SensorOption>(); }
                foreach (var o in options)
                    cb.Items.Add(new Item { Id = o.Id, Text = o.Name + (o.Value.HasValue ? "   (" + FormatValue(kv.Key, o.Value.Value) + ")" : "") });

                _settings.Bindings.TryGetValue(kv.Key, out var id);
                int idx = 0;
                for (int i = 1; i < cb.Items.Count; i++)
                    if (((Item)cb.Items[i]).Id == id) idx = i;
                cb.SelectedIndex = idx;
            }
        }

        private string FormatValue(MetricKind k, double v)
        {
            var inv = CultureInfo.InvariantCulture;
            if (MetricInfo.IsTemperature(k))
                return Math.Round(CoolerProtocol.ToUnit(v, _settings.Fahrenheit)).ToString(inv) + (_settings.Fahrenheit ? "°F" : "°C");
            switch (k)
            {
                case MetricKind.CpuPower:
                case MetricKind.GpuPower: return v.ToString("0.0", inv) + " W";
                case MetricKind.CpuClock:
                case MetricKind.GpuClock: return v.ToString("0", inv) + " MHz";
                case MetricKind.CpuVoltage: return v.ToString("0.000", inv) + " V";
                case MetricKind.Fan1:
                case MetricKind.Fan2: return v.ToString("0", inv) + " RPM";
                default: return v.ToString("0", inv) + " %";
            }
        }

        private void SaveValues()
        {
            _settings.Language = _lang.SelectedIndex == 1 ? "en" : "ar";
            _settings.Fahrenheit = _unit.SelectedIndex == 1;
            _settings.RefreshMs = RefreshValues[Math.Max(0, _refresh.SelectedIndex)];
            _settings.SendToDevice = _send.Checked;
            _settings.AutoStart = _autoStart.Checked;
            _settings.StartMinimized = _startMin.Checked;
            _settings.CloseToTray = _closeTray.Checked;
            foreach (var kv in _sensorCombos)
            {
                var item = kv.Value.SelectedItem as Item;
                if (item == null || string.IsNullOrEmpty(item.Id))
                    _settings.Bindings.Remove(kv.Key);
                else
                    _settings.Bindings[kv.Key] = item.Id;
            }
        }
    }
}
