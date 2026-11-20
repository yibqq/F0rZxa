# GPA Calculator — Android

A minimal, fully offline GPA calculator with an iOS-inspired design. Java 17 + XML, min SDK 26, target SDK 36.

<div dir="rtl">

حاسبة معدل بسيطة تعمل بدون إنترنت، بتصميم مستوحى من iOS. فيها قسمان فقط: **الحاسبة** و**السجل**. الواجهة بالعربية والإنجليزية حسب لغة الجهاز.

</div>

## Features

- **Calculator:** choose the 4.0 or 5.0 scale and add, edit or delete courses (name optional, credit hours, grade). The semester GPA updates live. An optional cumulative GPA combines the semester with your previous GPA and completed credits. One tap saves the result to history.
- **History:** saved calculations stored locally (SQLite), newest first, showing the GPA, date, course count and credits. Tap one to see its full details. Swipe to delete (with Undo), or delete from the detail page.
- The in-progress calculator is autosaved, so nothing is lost if the app is closed.

## Grade points

| Grade | A+ | A | B+ | B | C+ | C | D+ | D | F |
|---|---|---|---|---|---|---|---|---|---|
| 4.0 scale | 4.00 | 3.75 | 3.50 | 3.00 | 2.50 | 2.00 | 1.50 | 1.00 | 0.00 |
| 5.0 scale | 5.00 | 4.75 | 4.50 | 4.00 | 3.50 | 3.00 | 2.50 | 2.00 | 1.00 |

To use a different university's table, edit `model/Grade.java`. It is the only place grade points are defined.

## Accuracy

- Semester GPA = Σ(points × credits) ÷ Σ credits.
- Cumulative GPA = (previous GPA × previous credits + semester points) ÷ (previous credits + semester credits).
- All arithmetic uses `BigDecimal`. Rounding (half-up, 2 decimals) happens only for display.
- Inputs are validated as you type:
  - Credit hours: 1–12, set with a stepper.
  - Previous GPA: 0 up to the scale maximum, at most 3 decimals.
  - Completed credits: a whole number from 0 to 999.
  - Arabic-Indic digits are accepted.

## Design

- Grouped inset cards, iOS system colors (light and dark).
- Large titles that collapse into a translucent navigation bar.
- A custom segmented control and switch.
- A bottom sheet with a grade keypad and drag-to-dismiss.
- iOS-style alerts.
- Spring animations and light haptics.
- Edge-to-edge layout on every API level, and RTL layout for Arabic.

## Build

Open this folder in Android Studio and run, or from the command line:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # GPA math and input validation tests
```

## Structure

```
app/src/main/java/com/gpacalculator/
  model/   Grade, GradeScale, Course, GpaResult, HistoryRecord   (pure Java)
  logic/   GpaCalculator, NumberInput, PreviousRecord             (pure Java, unit-tested)
  data/    HistoryDatabase (SQLite), HistoryRepository, DraftStore
  ui/      MainActivity, CalculatorScreen, HistoryScreen, DetailActivity, CourseSheet, IosAlert…
  ui/widget/  SegmentedControl, IosSwitch, GroupCard, MaxWidthLinearLayout
  ui/util/    springs, haptics, insets, formatting
```

The only dependency is `androidx.recyclerview`. The app requests no permissions.
