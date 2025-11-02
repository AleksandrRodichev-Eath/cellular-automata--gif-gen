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

    public static Builder builder() {
        return new Builder();
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
