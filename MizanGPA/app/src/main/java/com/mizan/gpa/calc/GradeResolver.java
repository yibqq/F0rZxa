package com.mizan.gpa.calc;

import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;

import java.util.Map;

/** Decides which letter grade a course contributes to the GPA under a given calculation mode. */
public final class GradeResolver {
    private GradeResolver() {}

    public enum Mode {
        /** Only official final grades. */
        OFFICIAL,
        /** Official grade, else the expected grade, else the prediction from the marks. */
        EXPECTED,
        /** Official grade, else the best grade still reachable. */
        BEST,
        /** Official grade, else the grade already secured by the marks. */
        WORST
    }

    public static GradeDef resolve(Course c, GradeScale scale, boolean round, Mode mode) {
        return resolve(c, scale, round, mode, null);
    }

    /**
     * @param overrides optional what-if grades by course id; they take precedence over everything.
     */
    public static GradeDef resolve(Course c, GradeScale scale, boolean round, Mode mode, Map<String, String> overrides) {
        if (overrides != null && overrides.containsKey(c.id)) {
            GradeDef g = scale.find(overrides.get(c.id));
            if (g != null) return g;
        }
        GradeDef official = scale.find(c.finalGrade);
        if (official != null) return official;
        if (mode == Mode.OFFICIAL) return null;

        MarksCalculator.Result m = MarksCalculator.compute(c);
        switch (mode) {
            case EXPECTED: {
                GradeDef expected = scale.find(c.expectedGrade);
                if (expected != null) return expected;
                return m.hasMarks() ? scale.forPercent(m.projectedPercent(), round) : null;
            }
            case BEST:
                return m.hasMarks() ? scale.forPercent(m.maxPercent(), round) : scale.top();
            case WORST:
                return m.hasMarks() ? scale.forPercent(m.securedPercent(), round) : scale.bottom();
            default:
                return null;
        }
    }

    /** The predicted letter from the marks alone, ignoring manual expectations (null if no marks). */
    public static GradeDef predictedFromMarks(Course c, GradeScale scale, boolean round) {
        MarksCalculator.Result m = MarksCalculator.compute(c);
        return m.hasMarks() ? scale.forPercent(m.projectedPercent(), round) : null;
    }
}
