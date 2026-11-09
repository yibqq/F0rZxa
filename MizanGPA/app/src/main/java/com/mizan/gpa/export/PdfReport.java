package com.mizan.gpa.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;

import com.mizan.gpa.R;
import com.mizan.gpa.calc.AttendanceCalculator;
import com.mizan.gpa.calc.GpaCalculator;
import com.mizan.gpa.calc.GradeResolver;
import com.mizan.gpa.data.AppData;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.Semester;
import com.mizan.gpa.data.Settings;
import com.mizan.gpa.ui.Labels;
import com.mizan.gpa.util.Fmt;
import com.mizan.gpa.util.Ui;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

/** Renders the academic report (transcript, GPA summary, attendance) to an A4 PDF with Android's PdfDocument. */
public final class PdfReport {
    private static final int W = 595, H = 842, M = 40;
    private static final int BRAND = 0xFF0B6E69;
    private static final int BRAND_LIGHT = 0xFFE3F4F2;
    private static final int TEXT = 0xFF1B2120;
    private static final int MUTED = 0xFF5D6967;
    private static final int RULE = 0xFFD5DEDC;

    private final Context ctx;
    private final AppData data;
    private final boolean rtl;
    private final PageSink sink;
    private boolean pageOpen;
    private Canvas canvas;
    private float y;
    private int pageNo;

    private final TextPaint title = paint(20, true, Color.WHITE);
    private final TextPaint h2 = paint(13, true, TEXT);
    private final TextPaint body = paint(9.5f, false, TEXT);
    private final TextPaint bold = paint(9.5f, true, TEXT);
    private final TextPaint muted = paint(8.5f, false, MUTED);
    private final TextPaint headerCell = paint(8.5f, true, BRAND);
    private final TextPaint big = paint(18, true, BRAND);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Receives the report pages; production writes a PdfDocument, tests can draw to bitmaps. */
    public interface PageSink {
        Canvas startPage(int number, int width, int height);

        void finishPage();
    }

    private PdfReport(Context ctx, AppData data, PageSink sink) {
        this.ctx = ctx;
        this.data = data;
        this.rtl = Ui.isRtl(ctx);
        this.sink = sink;
    }

    public static void write(Context ctx, AppData data, OutputStream out) throws IOException {
        PdfDocument doc = new PdfDocument();
        try {
            render(ctx, data, new PageSink() {
                private PdfDocument.Page page;

                @Override
                public Canvas startPage(int number, int width, int height) {
                    page = doc.startPage(new PdfDocument.PageInfo.Builder(width, height, number).create());
                    return page.getCanvas();
                }

                @Override
                public void finishPage() {
                    doc.finishPage(page);
                }
            });
            doc.writeTo(out);
        } finally {
            doc.close();
        }
    }

    /** Lays out the report as A4 pages (595 x 842 points) on the given sink. */
    public static void render(Context ctx, AppData data, PageSink sink) {
        new PdfReport(ctx, data, sink).render();
    }

    private static TextPaint paint(float size, boolean b, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, b ? Typeface.BOLD : Typeface.NORMAL));
        return p;
    }

    // ---------------------------------------------------------------- layout primitives

    private void newPage() {
        if (pageOpen) finishPage();
        pageNo++;
        canvas = sink.startPage(pageNo, W, H);
        pageOpen = true;
        y = M;
    }

    private void finishPage() {
        String footer = ctx.getString(R.string.pdf_footer, pageNo);
        draw(footer, M, H - M + 12, W - 2 * M, muted, Layout.Alignment.ALIGN_CENTER);
        sink.finishPage();
        pageOpen = false;
    }

    private void ensure(float height) {
        if (y + height > H - M - 10) newPage();
    }

    private StaticLayout layout(CharSequence text, float width, TextPaint p, Layout.Alignment align, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(1, (int) width))
                .setAlignment(align)
                .setTextDirection(rtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR)
                .setIncludePad(false);
        if (maxLines > 0) b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        return b.build();
    }

    /** Draws text with its top at {@code top}; returns its height. */
    private float draw(CharSequence text, float x, float top, float width, TextPaint p, Layout.Alignment align) {
        StaticLayout l = layout(text, width, p, align, 0);
        canvas.save();
        canvas.translate(x, top);
        l.draw(canvas);
        canvas.restore();
        return l.getHeight();
    }

    /** x of a column in a table laid out from the start edge (mirrored in RTL). */
    private float colX(float[] widths, int i) {
        float before = 0;
        for (int k = 0; k < i; k++) before += widths[k];
        return rtl ? W - M - before - widths[i] : M + before;
    }

    private void tableRow(String[] cells, float[] widths, TextPaint p, boolean[] center, int background) {
        float pad = 5;
        float h = 0;
        StaticLayout[] ls = new StaticLayout[cells.length];
        for (int i = 0; i < cells.length; i++) {
            String cell = cells[i] == null ? "" : cells[i];
            // First-strong isolate: Latin cells such as "A+" keep their own direction inside RTL tables.
            if (rtl) cell = "\u2068" + cell + "\u2069";
            ls[i] = layout(cell, widths[i] - 2 * pad, p,
                    center[i] ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL, 2);
            h = Math.max(h, ls[i].getHeight());
        }
        h += 2 * pad;
        ensure(h);
        if (background != 0) {
            fill.setColor(background);
            canvas.drawRect(M, y, W - M, y + h, fill);
        }
        for (int i = 0; i < cells.length; i++) {
            canvas.save();
            canvas.translate(colX(widths, i) + pad, y + pad);
            ls[i].draw(canvas);
            canvas.restore();
        }
        y += h;
        fill.setColor(RULE);
        canvas.drawRect(M, y - 0.5f, W - M, y, fill);
    }

    // ---------------------------------------------------------------- content

    private void render() {
        Settings st = data.settings;
        newPage();

        // Banner
        fill.setColor(BRAND);
        canvas.drawRoundRect(new RectF(M, y, W - M, y + 74), 12, 12, fill);
        draw(ctx.getString(R.string.pdf_title), M + 18, y + 14, W - 2 * M - 36, title, Layout.Alignment.ALIGN_NORMAL);
        TextPaint sub = paint(9.5f, false, 0xFFCDEDEA);
        StringBuilder who = new StringBuilder();
        if (!st.studentName.trim().isEmpty()) who.append(st.studentName.trim());
        if (!st.university.trim().isEmpty()) who.append(who.length() > 0 ? " · " : "").append(st.university.trim());
        if (who.length() > 0) who.append(" · ");
        who.append(ctx.getString(R.string.pdf_generated, Fmt.dateLong(ctx, LocalDate.now())));
        draw(who, M + 18, y + 44, W - 2 * M - 36, sub, Layout.Alignment.ALIGN_NORMAL);
        y += 90;

        // Summary tiles
        GpaCalculator.Totals cum = GpaCalculator.cumulative(data, GradeResolver.Mode.OFFICIAL);
        GpaCalculator.Totals exp = GpaCalculator.cumulative(data, GradeResolver.Mode.EXPECTED);
        int courses = 0;
        for (Semester s : data.semesters) courses += s.courses.size();
        String[][] tiles = {
                {Fmt.gpa(cum.gpa()), ctx.getString(R.string.cumulative_gpa)},
                {Fmt.gpa(exp.gpa()), ctx.getString(R.string.expected_cumulative)},
                {Fmt.num(cum.credits), ctx.getString(R.string.completed_credits)},
                {String.valueOf(courses), ctx.getString(R.string.courses)},
        };
        float gap = 10, tw = (W - 2 * M - gap * 3) / 4f;
        for (int i = 0; i < tiles.length; i++) {
            float x = rtl ? W - M - (i + 1) * tw - i * gap : M + i * (tw + gap);
            fill.setColor(BRAND_LIGHT);
            canvas.drawRoundRect(new RectF(x, y, x + tw, y + 56), 10, 10, fill);
            draw(tiles[i][0], x, y + 9, tw, big, Layout.Alignment.ALIGN_CENTER);
            draw(tiles[i][1], x + 4, y + 34, tw - 8, muted, Layout.Alignment.ALIGN_CENTER);
        }
        y += 68;
        String scaleLine = ctx.getString(R.string.pdf_scale, Labels.presetName(ctx, st.scale.preset), Fmt.gpa(st.scale.maxPoints()));
        if (st.priorCredits > 0) scaleLine += " · " + ctx.getString(R.string.prior_included, Fmt.gpa(st.priorGpa), Fmt.num(st.priorCredits));
        y += draw(scaleLine, M, y, W - 2 * M, muted, Layout.Alignment.ALIGN_NORMAL) + 14;

        // Transcript
        float[] widths = {70, W - 2 * M - 70 - 50 - 55 - 60, 50, 55, 60};
        boolean[] center = {false, false, true, true, true};
        String[] head = {ctx.getString(R.string.pdf_code), ctx.getString(R.string.course), ctx.getString(R.string.pdf_credits),
                ctx.getString(R.string.pdf_grade), ctx.getString(R.string.pdf_points)};
        GpaCalculator.Totals running = GpaCalculator.prior(st);
        for (Semester s : data.semesters) {
            ensure(70);
            GpaCalculator.Totals off = GpaCalculator.semester(s, st, GradeResolver.Mode.OFFICIAL);
            running = running.plus(off);
            y += draw(s.name, M, y, W - 2 * M, h2, Layout.Alignment.ALIGN_NORMAL) + 2;
            String line = ctx.getString(R.string.pdf_semester_line, Fmt.gpa(off.gpa()), Fmt.gpa(running.gpa()), Fmt.num(s.totalCredits()));
            y += draw(line, M, y, W - 2 * M, muted, Layout.Alignment.ALIGN_NORMAL) + 6;
            tableRow(head, widths, headerCell, center, BRAND_LIGHT);
            if (s.courses.isEmpty()) {
                y += 4;
                y += draw(ctx.getString(R.string.pdf_no_courses), M, y, W - 2 * M, muted, Layout.Alignment.ALIGN_NORMAL);
            }
            for (Course c : s.courses) {
                GradeDef official = st.scale.find(c.finalGrade);
                GradeDef shown = official != null ? official
                        : GradeResolver.resolve(c, st.scale, st.roundPercent, GradeResolver.Mode.EXPECTED);
                String grade = official != null ? official.letter
                        : shown != null ? "≈ " + shown.letter + " *" : ctx.getString(R.string.pdf_in_progress);
                String points = shown == null ? Fmt.DASH : Fmt.gpa(shown.points * c.credits);
                String name = c.name + (c.countInGpa ? "" : " (" + ctx.getString(R.string.not_in_gpa) + ")");
                tableRow(new String[]{c.code, name, Fmt.num(c.credits), grade, points}, widths, body, center, 0);
            }
            y += 16;
        }
        ensure(20);
        y += draw(ctx.getString(R.string.pdf_expected_note), M, y, W - 2 * M, muted, Layout.Alignment.ALIGN_NORMAL) + 16;

        // Attendance of the current semester
        Semester cur = data.currentSemester();
        if (cur != null && !cur.courses.isEmpty()) {
            ensure(80);
            y += draw(ctx.getString(R.string.pdf_attendance_title, cur.name), M, y, W - 2 * M, h2, Layout.Alignment.ALIGN_NORMAL) + 2;
            y += draw(ctx.getString(st.deprivedOnReach ? R.string.attendance_rule_reach : R.string.attendance_rule_exceed,
                    Fmt.pct(st.absenceLimit)), M, y, W - 2 * M, muted, Layout.Alignment.ALIGN_NORMAL) + 6;
            float[] aw = {W - 2 * M - 70 - 60 - 70 - 90, 70, 60, 70, 90};
            boolean[] ac = {false, true, true, true, true};
            tableRow(new String[]{ctx.getString(R.string.course), ctx.getString(R.string.absences_label), ctx.getString(R.string.pdf_percent),
                    ctx.getString(R.string.pdf_remaining), ctx.getString(R.string.pdf_status)}, aw, headerCell, ac, BRAND_LIGHT);
            for (Course c : cur.courses) {
                AttendanceCalculator.Stats a = AttendanceCalculator.compute(c, cur, st);
                tableRow(new String[]{c.displayName(),
                        a.hasTotal() ? a.counted + " / " + a.allowed : String.valueOf(a.counted),
                        Fmt.pct(a.percent),
                        a.hasTotal() ? String.valueOf(Math.max(0, a.remaining)) : Fmt.DASH,
                        Ui.statusLabel(ctx, a.status)}, aw, body, ac, 0);
            }
        }
        finishPage();
    }
}
