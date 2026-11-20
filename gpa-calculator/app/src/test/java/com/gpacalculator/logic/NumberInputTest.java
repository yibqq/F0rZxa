package com.gpacalculator.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gpacalculator.model.GradeScale;

import org.junit.Test;

import java.math.BigDecimal;

public class NumberInputTest {

    @Test
    public void parsesAsciiAndArabicDigits() {
        assertEquals(0, new BigDecimal("3.75").compareTo(NumberInput.parse("3.75")));
        assertEquals(0, new BigDecimal("3.75").compareTo(NumberInput.parse("٣٫٧٥")));
        assertEquals(0, new BigDecimal("3.75").compareTo(NumberInput.parse("3,75")));
        assertEquals(0, new BigDecimal("4").compareTo(NumberInput.parse("۴")));
        assertEquals(0, new BigDecimal("0.5").compareTo(NumberInput.parse(".5")));
        assertEquals(0, new BigDecimal("3").compareTo(NumberInput.parse("3.")));
    }

    @Test
    public void rejectsMalformedInput() {
        assertNull(NumberInput.parse(""));
        assertNull(NumberInput.parse("."));
        assertNull(NumberInput.parse("-1"));
        assertNull(NumberInput.parse("1.2.3"));
        assertNull(NumberInput.parse("abc"));
        assertNull(NumberInput.parse("1e3"));
    }

    @Test
    public void gpaDraftFilter() {
        assertTrue(NumberInput.isGpaDraft(""));
        assertTrue(NumberInput.isGpaDraft("3"));
        assertTrue(NumberInput.isGpaDraft("3."));
        assertTrue(NumberInput.isGpaDraft("3.756"));
        assertTrue(NumberInput.isGpaDraft(".5"));
        assertFalse(NumberInput.isGpaDraft("34"));
        assertFalse(NumberInput.isGpaDraft("3.7561"));
        assertFalse(NumberInput.isGpaDraft("-"));
    }

    @Test
    public void wholeNumberDraftFilter() {
        assertTrue(NumberInput.isWholeNumberDraft("", 3));
        assertTrue(NumberInput.isWholeNumberDraft("120", 3));
        assertTrue(NumberInput.isWholeNumberDraft("١٢٠", 3));
        assertFalse(NumberInput.isWholeNumberDraft("1200", 3));
        assertFalse(NumberInput.isWholeNumberDraft("12.5", 3));
    }

    @Test
    public void previousRecordValidation() {
        assertEquals(PreviousRecord.Status.INCOMPLETE, PreviousRecord.parse("", "", GradeScale.FOUR).status);
        assertEquals(PreviousRecord.Status.INCOMPLETE, PreviousRecord.parse("3.2", "", GradeScale.FOUR).status);
        assertEquals(PreviousRecord.Status.VALID, PreviousRecord.parse("3.2", "45", GradeScale.FOUR).status);
        assertEquals(PreviousRecord.Status.VALID, PreviousRecord.parse("4", "0", GradeScale.FOUR).status);
        assertEquals(PreviousRecord.Status.GPA_OUT_OF_RANGE, PreviousRecord.parse("4.5", "45", GradeScale.FOUR).status);
        assertEquals(PreviousRecord.Status.VALID, PreviousRecord.parse("4.5", "45", GradeScale.FIVE).status);
        assertEquals(PreviousRecord.Status.GPA_OUT_OF_RANGE, PreviousRecord.parse("5.01", "45", GradeScale.FIVE).status);
        assertEquals(PreviousRecord.Status.CREDITS_OUT_OF_RANGE, PreviousRecord.parse("3", "1000", GradeScale.FIVE).status);
        assertEquals(PreviousRecord.Status.CREDITS_OUT_OF_RANGE, PreviousRecord.parse("3", "12.5", GradeScale.FIVE).status);
        assertEquals(PreviousRecord.Status.VALID, PreviousRecord.parse("٣٫٢", "٤٥", GradeScale.FOUR).status);
    }
}
