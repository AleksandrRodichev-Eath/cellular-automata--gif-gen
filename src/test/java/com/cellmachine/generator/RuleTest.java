package com.cellmachine.generator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RuleTest {

    @Test
    void parsesStandardRule() {
        Rule rule = Rule.parse("B3/S23");
        assertTrue(rule.shouldLive(false, 3));
        assertTrue(rule.shouldLive(true, 2));
        assertTrue(rule.shouldLive(true, 3));
        assertFalse(rule.shouldLive(false, 2));
    }

    @Test
    void rejectsDuplicateDigits() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Rule.parse("B33/S23"));
        assertTrue(ex.getMessage().contains("Digit 3 is duplicated"));
    }

    @Test
    void rejectsInvalidPrefix() {
        assertThrows(IllegalArgumentException.class, () -> Rule.parse("C3/S23"));
    }

    @Test
    void rejectsOutOfRangeDigits() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Rule.parse("B9/S23"));
        assertTrue(ex.getMessage().contains("out of range"));
    }
}
