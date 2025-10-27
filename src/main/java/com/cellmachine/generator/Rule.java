package com.cellmachine.generator;

import java.util.Arrays;

public final class Rule {

    private final boolean[] born = new boolean[9];
    private final boolean[] survive = new boolean[9];
    private final String label;

    private Rule(String label) {
        this.label = label;
    }

    public static Rule parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Rule string is empty");
        }
        String trimmed = raw.trim();
        Rule rule = new Rule(trimmed);
        String[] parts = trimmed.split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Rule must follow B#/S# format");
        }
        boolean[] born = parseSegment(parts[0], 'B');
        boolean[] survive = parseSegment(parts[1], 'S');
        System.arraycopy(born, 0, rule.born, 0, born.length);
        System.arraycopy(survive, 0, rule.survive, 0, survive.length);
        return rule;
    }

    public static Rule defaultLife() {
        return parse("B3/S23");
    }

    public boolean shouldLive(boolean currentlyAlive, int neighborCount) {
        if (neighborCount < 0 || neighborCount > 8) {
            throw new IllegalArgumentException("Neighbor count must be between 0 and 8");
        }
        return currentlyAlive ? survive[neighborCount] : born[neighborCount];
    }

    public String label() {
        return label;
    }

    private static boolean[] parseSegment(String segment, char prefix) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("Rule must follow B#/S# format");
        }
        segment = segment.trim();
        if (segment.isEmpty()) {
            throw new IllegalArgumentException("Rule must follow B#/S# format");
        }
        char first = Character.toUpperCase(segment.charAt(0));
        if (first != prefix) {
            throw new IllegalArgumentException("Rule must follow B#/S# format");
        }
        boolean[] flags = new boolean[9];
        for (int i = 1; i < segment.length(); i++) {
            char ch = segment.charAt(i);
            if (!Character.isDigit(ch)) {
                throw new IllegalArgumentException("Invalid digit '" + ch + "' in rule");
            }
            int value = ch - '0';
            if (value < 0 || value > 8) {
                throw new IllegalArgumentException("Neighbor count " + value + " is out of range 0-8");
            }
            if (flags[value]) {
                throw new IllegalArgumentException("Digit " + value + " is duplicated in rule");
            }
            flags[value] = true;
        }
        return flags;
    }

    @Override
    public String toString() {
        return label;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Rule other)) {
            return false;
        }
        return label.equals(other.label) && Arrays.equals(born, other.born) && Arrays.equals(survive, other.survive);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(born);
        result = 31 * result + Arrays.hashCode(survive);
        result = 31 * result + label.hashCode();
        return result;
    }
}
