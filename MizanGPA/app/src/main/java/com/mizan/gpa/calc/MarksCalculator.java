package com.mizan.gpa.calc;

import com.mizan.gpa.data.Assessment;
import com.mizan.gpa.data.Course;
import com.mizan.gpa.data.GradeDef;
import com.mizan.gpa.data.GradeScale;

import java.util.ArrayList;
import java.util.List;

/**
 * Course marks: totals, percentages, predicted letter and what is needed in the remaining work.
 *
 * <p>The course total is the larger of the course's total marks (normally 100) and the sum of all
 * assessment weights. Marks of the total that are not covered by any assessment (for example a final
 * exam that has not been added yet) are treated as still to come.
 */
public final class MarksCalculator {
    private static final double EPS = 1e-9;

    private MarksCalculator() {}

    public static final class Result {
        /** Marks earned so far. */
        public double earned;
        /** Marks of the assessments that were graded. */
        public double gradedWeight;
        /** Course total (normally 100). */
        public double totalWeight;
        /** Marks still to be decided: ungraded assessments plus anything not assigned yet. */
        public double remainingWeight;
        public int gradedCount;
        public int ungradedCount;
        /** The only remaining assessment, if exactly one is left and it covers all remaining marks. */
        public Assessment lastRemaining;

        public boolean hasMarks() {
            return gradedWeight > EPS;
        }

        public boolean isComplete() {
            return hasMarks() && remainingWeight <= EPS;
        }

        /** Performance on graded work only, in percent (NaN if nothing is graded). */
        public double currentPercent() {
            return hasMarks() ? earned / gradedWeight * 100.0 : Double.NaN;
        }

        /** Percent of the whole course already secured (the worst case). */
        public double securedPercent() {
            return totalWeight > 0 ? earned / totalWeight * 100.0 : Double.NaN;
        }

        /** Percent if every remaining mark is earned (the best case). */
        public double maxPercent() {
            return totalWeight > 0 ? (earned + remainingWeight) / totalWeight * 100.0 : Double.NaN;
        }

        /**
         * Expected final percent: the final percent when complete, otherwise the current performance
         * applied to the remaining marks.
         */
        public double projectedPercent() {
            if (isComplete()) return securedPercent();
            // Keeping the same performance on the remaining marks gives the current percentage.
            return currentPercent();
        }
    }

    public static Result compute(Course c) {
        Result r = new Result();
        double sumWeights = 0;
        Assessment lastUngraded = null;
        for (Assessment a : c.assessments) {
            double w = Math.max(0, a.weight);
            sumWeights += w;
            if (a.isGraded()) {
                r.gradedWeight += w;
                r.earned += a.contribution();
                r.gradedCount++;
            } else {
                r.ungradedCount++;
                lastUngraded = a;
            }
        }
        r.totalWeight = Math.max(c.totalMarks > 0 ? c.totalMarks : 100, sumWeights);
        r.remainingWeight = Math.max(0, r.totalWeight - r.gradedWeight);
        if (r.ungradedCount == 1 && Math.abs(Math.max(0, lastUngraded.weight) - r.remainingWeight) < 1e-6) {
            r.lastRemaining = lastUngraded;
        }
        return r;
    }

    public enum State { SECURED, POSSIBLE, IMPOSSIBLE }

    /** What the student needs in the remaining work to finish with a given grade. */
    public static final class Requirement {
        public GradeDef grade;
        public State state;
        /** Marks needed out of the remaining marks. */
        public double neededMarks;
        /** Needed share of the remaining marks, in percent. */
        public double neededPercent;
        /** When one assessment is left: score needed on it, in its own units. */
        public double neededScore = Double.NaN;
        public double neededOutOf = Double.NaN;
    }

    public static List<Requirement> requirements(Result r, GradeScale scale, boolean round) {
        List<Requirement> out = new ArrayList<>();
        for (GradeDef g : scale.grades) {
            Requirement q = new Requirement();
            q.grade = g;
            double target = GradeScale.effectiveMin(g, round) / 100.0 * r.totalWeight;
            double need = target - r.earned;
            q.neededMarks = Math.max(0, need);
            if (need <= EPS) {
                q.state = State.SECURED;
                q.neededPercent = 0;
            } else if (need > r.remainingWeight + EPS) {
                q.state = State.IMPOSSIBLE;
                q.neededPercent = r.remainingWeight > 0 ? need / r.remainingWeight * 100.0 : Double.POSITIVE_INFINITY;
            } else {
                q.state = State.POSSIBLE;
                q.neededPercent = need / r.remainingWeight * 100.0;
                if (r.lastRemaining != null && r.lastRemaining.weight > 0) {
                    q.neededOutOf = r.lastRemaining.outOf;
                    q.neededScore = need / r.lastRemaining.weight * r.lastRemaining.outOf;
                }
            }
            out.add(q);
        }
        return out;
    }
}
