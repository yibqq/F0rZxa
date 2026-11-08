using System.Collections.Generic;

namespace MajestyMonitor.Core
{
    /// <summary>Arabic / English UI text.</summary>
    public static class Strings
    {
        public static string Lang = "ar";
        public static bool Rtl => Lang == "ar";

        private static readonly Dictionary<string, string[]> T = new Dictionary<string, string[]>
        {
            //               English                         العربية
            ["AppSub"] = new[] { "Air Cooler Monitor", "مراقب المبرد الهوائي" },
            ["Connected"] = new[] { "Cooler connected", "المبرد متصل" },
            ["ConnectedN"] = new[] { "{0} coolers connected", "{0} مبردات متصلة" },
            ["Disconnected"] = new[] { "Searching for cooler…", "جارٍ البحث عن المبرد…" },
            ["SendingOff"] = new[] { "Display output off", "الإرسال للشاشة متوقف" },
            ["Temperature"] = new[] { "Temperature", "الحرارة" },
            ["Usage"] = new[] { "Usage", "الاستخدام" },
            ["Power"] = new[] { "Power", "الطاقة" },
            ["Clock"] = new[] { "Clock", "التردد" },
            ["Voltage"] = new[] { "Voltage", "الجهد" },
            ["VRAM"] = new[] { "Memory", "الذاكرة" },
            ["History"] = new[] { "Temperature history", "سجل الحرارة" },
            ["Last2Min"] = new[] { "last 2 minutes", "آخر دقيقتين" },
            ["Fans"] = new[] { "Fans", "المراوح" },
            ["Fan1"] = new[] { "Fan 1", "مروحة 1" },
            ["Fan2"] = new[] { "Fan 2", "مروحة 2" },
            ["Ram"] = new[] { "RAM", "الرام" },
            ["RamUsed"] = new[] { "{0:0.0} / {1:0} GB used", "مستخدم {0:0.0} من {1:0} جيجا" },
            ["NoData"] = new[] { "N/A", "غير متاح" },
            ["Loading"] = new[] { "Reading sensors…", "جارٍ قراءة الحساسات…" },
            ["Ready"] = new[] { "Sensors live", "الحساسات تعمل" },
            ["PawnIO"] = new[] { "CPU temperature needs the free PawnIO driver.", "حرارة المعالج تحتاج تعريف PawnIO المجاني." },
            ["Install"] = new[] { "Install", "تثبيت" },
            ["Settings"] = new[] { "Settings", "الإعدادات" },
            ["Show"] = new[] { "Show", "إظهار" },
            ["Exit"] = new[] { "Exit", "خروج" },
            ["TrayHint"] = new[] { "Still running in the tray and updating your cooler.", "البرنامج يعمل في شريط المهام ويحدّث شاشة المبرد." },
            ["General"] = new[] { "General", "عام" },
            ["Language"] = new[] { "Language", "اللغة" },
            ["Units"] = new[] { "Temperature unit", "وحدة الحرارة" },
            ["AutoStart"] = new[] { "Start with Windows", "التشغيل مع ويندوز" },
            ["StartMin"] = new[] { "Start minimized to tray", "البدء مصغّراً في شريط المهام" },
            ["CloseTray"] = new[] { "Close button keeps it running in tray", "زر الإغلاق يبقيه يعمل في شريط المهام" },
            ["SendDevice"] = new[] { "Send data to the cooler display", "إرسال البيانات لشاشة المبرد" },
            ["Refresh"] = new[] { "Refresh interval", "سرعة التحديث" },
            ["Sensors"] = new[] { "Sensors", "الحساسات" },
            ["Auto"] = new[] { "Automatic (recommended)", "تلقائي (مستحسن)" },
            ["Save"] = new[] { "Save", "حفظ" },
            ["Cancel"] = new[] { "Cancel", "إلغاء" },
            ["Reset"] = new[] { "Reset sensors", "إعادة الحساسات" },
            ["M_CpuTemp"] = new[] { "CPU temperature", "حرارة المعالج" },
            ["M_CpuLoad"] = new[] { "CPU usage", "استخدام المعالج" },
            ["M_CpuPower"] = new[] { "CPU power", "طاقة المعالج" },
            ["M_CpuClock"] = new[] { "CPU clock", "تردد المعالج" },
            ["M_CpuVoltage"] = new[] { "CPU voltage", "جهد المعالج" },
            ["M_GpuTemp"] = new[] { "GPU temperature", "حرارة كرت الشاشة" },
            ["M_GpuLoad"] = new[] { "GPU usage", "استخدام كرت الشاشة" },
            ["M_GpuPower"] = new[] { "GPU power", "طاقة كرت الشاشة" },
            ["M_GpuClock"] = new[] { "GPU clock", "تردد كرت الشاشة" },
            ["M_GpuMemLoad"] = new[] { "GPU memory", "ذاكرة كرت الشاشة" },
            ["M_Fan1"] = new[] { "Fan 1 speed", "سرعة المروحة 1" },
            ["M_Fan2"] = new[] { "Fan 2 speed", "سرعة المروحة 2" },
            ["Error"] = new[] { "Error", "خطأ" },
        };

        public static string Get(string key)
        {
            if (!T.TryGetValue(key, out var v))
                return key;
            return Lang == "ar" ? v[1] : v[0];
        }

        public static string Metric(MetricKind k) => Get("M_" + k);

        private static readonly string[] DaysAr = { "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت" };
        private static readonly string[] MonthsAr = { "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر" };
        private static readonly string[] DaysEn = { "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
        private static readonly string[] MonthsEn = { "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" };

        /// <summary>Gregorian date with fixed names, independent of the Windows locale.</summary>
        public static string Date(System.DateTime d) => Lang == "ar"
            ? DaysAr[(int)d.DayOfWeek] + "، " + d.Day + " " + MonthsAr[d.Month - 1]
            : DaysEn[(int)d.DayOfWeek] + ", " + d.Day + " " + MonthsEn[d.Month - 1];
    }
}
