package com.cellmachine.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SimulationOptions {

    public static final int DEFAULT_STEPS = 100;
    public static final int DEFAULT_DELAY_CS = 6;

    private final int steps;
    private final Rule rule;
    private final String ruleLabel;
    private final Double density;
    private final boolean[] initMask;
    private final List<CellCoordinate> seedCells;
    private final boolean wrap;
    private final int delayCs;
    private final SimulationDimensions dimensions;
    private final long randomSeed;
    private final SimulationOutputFormat outputFormat;
    private static final String NULL_TOKEN = "-";

    private final Palette2D palette;
    private final Integer progressLogPercentStep = 10;

    private SimulationOptions(Builder builder) {
        this.steps = builder.steps;
        this.rule = builder.rule;
        this.ruleLabel = builder.ruleLabel;
        this.density = builder.density;
        this.initMask = builder.initMask == null ? null : builder.initMask.clone();
        this.seedCells = List.copyOf(builder.seedCells);
        this.wrap = builder.wrap;
        this.delayCs = builder.delayCs;
        this.dimensions = builder.dimensions;
        this.randomSeed = builder.randomSeed;
        this.outputFormat = builder.outputFormat;
        this.palette = builder.palette;
//        this.progressLogPercentStep = builder.progressLogPercentStep;
    }

    public int steps() {
        return steps;
    }

    public Rule rule() {
        return rule;
    }

    public String ruleLabel() {
        return ruleLabel;
    }

    public Double density() {
        return density;
    }

    public boolean[] initMask() {
        return initMask == null ? null : initMask.clone();
    }

    public List<CellCoordinate> seedCells() {
        return seedCells;
    }

    public boolean wrap() {
        return wrap;
    }

    public int delayCs() {
        return delayCs;
    }

    public SimulationDimensions dimensions() {
        return dimensions;
    }

    public long randomSeed() {
        return randomSeed;
    }

    public SimulationOutputFormat outputFormat() {
        return outputFormat;
    }

    public Palette2D palette() {
        return palette;
    }

    public Integer progressLogPercentStep() {
        return progressLogPercentStep;
    }

    public String serialize() {
        List<String> parts = new ArrayList<>();
        parts.add(Integer.toString(steps));
        String[] ruleParts = rule.label().split("/", -1);
        if (ruleParts.length != 2) {
            throw new IllegalStateException("Rule label must contain born/survive segments");
        }
        parts.add(ruleParts[0]);
        parts.add(ruleParts[1]);
        String ruleLabelPart = ruleLabel == null || ruleLabel.equals(ruleParts[0] + "/" + ruleParts[1])
                ? NULL_TOKEN
                : ruleLabel;
        parts.add(ruleLabelPart);
        parts.add(density == null ? NULL_TOKEN : Double.toString(density));
        parts.add(initMask == null ? NULL_TOKEN : SeedService.maskToLabel(initMask));
        parts.add(seedCells.isEmpty() ? NULL_TOKEN : serializeSeedCells(seedCells));
        parts.add(wrap ? "1" : "0");
        parts.add(Integer.toString(delayCs));
        parts.add(Integer.toString(dimensions.width()));
        parts.add(Integer.toString(dimensions.height()));
        parts.add(Integer.toString(dimensions.scale()));
        parts.add(Long.toString(randomSeed));
        parts.add(palette.name());
        return String.join("_", parts);
    }

    public static SimulationOptions deserialize(String serialized) {
        Objects.requireNonNull(serialized, "serialized");
        String[] parts = serialized.split("_", -1);
        if (parts.length != 14) {
            throw new IllegalArgumentException("Serialized options must contain 14 parts but found " + parts.length);
        }
        int idx = 0;
        int steps = parseInt(parts[idx++], "steps");
        String bornSegment = requireToken(parts[idx++], "rule born segment");
        String surviveSegment = requireToken(parts[idx++], "rule survive segment");
        String serializedRule = bornSegment + "/" + surviveSegment;
        String ruleLabelToken = parts[idx++];
        String ruleLabel = (ruleLabelToken == null || ruleLabelToken.isEmpty() || NULL_TOKEN.equals(ruleLabelToken))
                ? serializedRule
                : ruleLabelToken;
        Double density = parseDouble(parts[idx++]);
        boolean[] initMask = parseInitMask(parts[idx++]);
        List<CellCoordinate> seedCells = parseSeedCells(parts[idx++]);
        boolean wrap = parseBoolean(parts[idx++]);
        int delayCs = parseInt(parts[idx++], "delay");
        int width = parseInt(parts[idx++], "width");
        int height = parseInt(parts[idx++], "height");
        int scale = parseInt(parts[idx++], "scale");
        long randomSeed = parseLong(parts[idx++], "random seed");
        Palette2D palette = Palette2D.valueOf(requireToken(parts[idx], "palette"));

        SimulationOptions.Builder builder = SimulationOptions.builder()
                .steps(steps)
                .rule(Rule.parse(bornSegment + "/" + surviveSegment))
                .ruleLabel(ruleLabel)
                .wrap(wrap)
                .delayCs(delayCs)
                .dimensions(new SimulationDimensions(width, height, scale))
                .randomSeed(randomSeed)
                .outputFormat(SimulationOutputFormat.MP4)
                .palette(palette);
        if (density != null) {
            builder.density(density);
        }
        if (initMask != null) {
            builder.initMask(initMask);
        }
        if (!seedCells.isEmpty()) {
            builder.seedCells(seedCells);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String serializeSeedCells(List<CellCoordinate> cells) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            CellCoordinate coordinate = cells.get(i);
            if (i > 0) {
                builder.append(';');
            }
            builder.append(coordinate.x()).append(':').append(coordinate.y());
        }
        return builder.toString();
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isEmpty() || NULL_TOKEN.equals(value)) {
            throw new IllegalArgumentException(label + " is missing in serialized options");
        }
        return value;
    }

    private static Double parseDouble(String value) {
        if (value == null || value.isEmpty() || NULL_TOKEN.equals(value)) {
            return null;
        }
        return Double.valueOf(value);
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || NULL_TOKEN.equals(value)) {
            return null;
        }
        return Integer.valueOf(value);
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(requireToken(value, label));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
        }
    }

    private static long parseLong(String value, String label) {
        try {
            return Long.parseLong(requireToken(value, label));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + label + " value: '" + value + "'", ex);
        }
    }

    private static boolean[] parseInitMask(String value) {
        if (value == null || value.isEmpty() || NULL_TOKEN.equals(value)) {
            return null;
        }
        return SeedService.parseInitMask(value);
    }

    private static List<CellCoordinate> parseSeedCells(String value) {
        List<CellCoordinate> cells = new ArrayList<>();
        if (value == null || value.isEmpty() || NULL_TOKEN.equals(value)) {
            return cells;
        }
        String[] entries = value.split(";", -1);
        for (String entry : entries) {
            if (entry.isEmpty()) {
                continue;
            }
            String[] coordinates = entry.split(":", -1);
            if (coordinates.length != 2) {
                throw new IllegalArgumentException("Invalid seed cell entry '" + entry + "'");
            }
            try {
                int x = Integer.parseInt(coordinates[0]);
                int y = Integer.parseInt(coordinates[1]);
                cells.add(new CellCoordinate(x, y));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid seed cell entry '" + entry + "'", ex);
            }
        }
        return cells;
    }

    private static boolean parseBoolean(String value) {
        String token = requireToken(value, "wrap");
        if ("1".equals(token) || "true".equalsIgnoreCase(token)) {
            return true;
        }
        if ("0".equals(token) || "false".equalsIgnoreCase(token)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value '" + value + "'");
    }

    public static final class Builder {
        private int steps = DEFAULT_STEPS;
        private Rule rule = Rule.defaultLife();
        private String ruleLabel = Rule.defaultLife().label();
        private Double density;
        private boolean[] initMask;
        private final List<CellCoordinate> seedCells = new ArrayList<>();
        private boolean wrap = true;
        private int delayCs = DEFAULT_DELAY_CS;
        private SimulationDimensions dimensions = SimulationDimensions.defaults();
        private long randomSeed = SeedService.DEFAULT_RANDOM_SEED;
        private SimulationOutputFormat outputFormat = SimulationOutputFormat.MP4;
        private Palette2D palette = Palette2D.ysConcreteJungle;
        private Integer progressLogPercentStep;

        public Builder steps(int steps) {
            if (steps <= 0) {
                throw new IllegalArgumentException("Steps must be positive");
            }
            this.steps = steps;
            return this;
        }

        public Builder rule(Rule rule) {
            this.rule = Objects.requireNonNull(rule, "rule");
            return this;
        }

        public Builder ruleLabel(String ruleLabel) {
            this.ruleLabel = Objects.requireNonNull(ruleLabel, "ruleLabel");
            return this;
        }

        public Builder density(Double density) {
            if (density != null) {
                if (Double.isNaN(density) || density < 0.0 || density > 1.0) {
                    throw new IllegalArgumentException("Density must be between 0.0 and 1.0 inclusive");
                }
            }
            this.density = density;
            return this;
        }

        public Builder initMask(boolean[] mask) {
            this.initMask = mask == null ? null : mask.clone();
            return this;
        }

        public Builder seedCells(List<CellCoordinate> cells) {
            this.seedCells.clear();
            if (cells != null) {
                this.seedCells.addAll(cells);
            }
            return this;
        }

        public Builder wrap(boolean wrap) {
            this.wrap = wrap;
            return this;
        }

        public Builder delayCs(int delayCs) {
            if (delayCs <= 0) {
                throw new IllegalArgumentException("Delay must be positive");
            }
            this.delayCs = delayCs;
            return this;
        }

        public Builder dimensions(SimulationDimensions dimensions) {
            this.dimensions = Objects.requireNonNull(dimensions, "dimensions");
            return this;
        }

        public Builder randomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
            return this;
        }

        public Builder outputFormat(SimulationOutputFormat outputFormat) {
            this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat");
            return this;
        }

        public Builder palette(Palette2D palette) {
            this.palette = Objects.requireNonNull(palette, "palette");
            return this;
        }

        public Builder progressLogPercentStep(Integer percentStep) {
            if (percentStep != null) {
                if (percentStep <= 0 || percentStep > 100) {
                    throw new IllegalArgumentException("Progress log percent step must be between 1 and 100");
                }
            }
            this.progressLogPercentStep = percentStep;
            return this;
        }

        public SimulationOptions build() {
            if (rule == null) {
                throw new IllegalStateException("Rule must be provided");
            }
            if (!seedCells.isEmpty() && initMask != null) {
                throw new IllegalStateException("Seed cells and init mask are mutually exclusive");
            }
            if (outputFormat == null) {
                throw new IllegalStateException("Output format must be provided");
            }
            if (palette == null) {
                throw new IllegalStateException("Palette must be provided");
            }
            return new SimulationOptions(this);
        }
    }
}
