package com.cellmachine.generator;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomSimulationFactory {

    private static final int RANDOM_STEPS = 100;
    private static final double RANDOM_DENSITY = 0.05d;
    private static final int GRID_WIDTH = 200;
    private static final int GRID_HEIGHT = 200;

    private RandomSimulationFactory() {
    }

    public static RandomSelection create() {
        return create(ThreadLocalRandom.current());
    }

    static RandomSelection create(ThreadLocalRandom random) {
        Objects.requireNonNull(random, "random");
        String birthDigits = randomRuleDigits(random);
        String survivalDigits = randomRuleDigits(random);
        boolean[] mask = randomMask(random);
        return new RandomSelection(birthDigits, survivalDigits, mask);
    }

    public static SimulationOptions buildOptions(RandomSelection selection) {
        Objects.requireNonNull(selection, "selection");
        String ruleLabel = selection.ruleLabel();
        Rule rule = Rule.parse(ruleLabel);
        SimulationOptions.Builder builder = SimulationOptions.builder()
                .rule(rule)
                .ruleLabel(ruleLabel)
                .dimensions(new SimulationDimensions(GRID_WIDTH, GRID_HEIGHT, SimulationDimensions.DEFAULT_SCALE))
                .steps(RANDOM_STEPS)
                .delayCs(SimulationOptions.DEFAULT_DELAY_CS)
                .wrap(true)
                .density(RANDOM_DENSITY)
                .outputFormat(SimulationOutputFormat.MP4)
                .palette(Palette2D.paperback2)
                .randomSeed(SeedService.DEFAULT_RANDOM_SEED);
        boolean[] mask = selection.mask();
        if (mask != null) {
            builder.initMask(mask);
        }
        return builder.build();
    }

    private static String randomRuleDigits(ThreadLocalRandom random) {
        int mask = random.nextInt(1, 1 << 9);
        StringBuilder digits = new StringBuilder();
        for (int value = 0; value < 9; value++) {
            if ((mask & (1 << value)) != 0) {
                digits.append(value);
            }
        }
        return digits.toString();
    }

    private static boolean[] randomMask(ThreadLocalRandom random) {
        int maskSide = random.nextInt(SeedService.MIN_MASK_SIDE, SeedService.MAX_MASK_SIDE + 1);
        boolean[] mask = new boolean[maskSide * maskSide];
        int active = 0;
        for (int idx = 0; idx < mask.length; idx++) {
            boolean on = random.nextBoolean();
            mask[idx] = on;
            if (on) {
                active++;
            }
        }
        if (active == 0) {
            mask[random.nextInt(mask.length)] = true;
        }
        return mask;
    }

    public record RandomSelection(String birthDigits, String survivalDigits, boolean[] mask) {

        public RandomSelection {
            Objects.requireNonNull(birthDigits, "birthDigits");
            Objects.requireNonNull(survivalDigits, "survivalDigits");
            if (birthDigits.isEmpty() || survivalDigits.isEmpty()) {
                throw new IllegalArgumentException("Rule digits must not be empty");
            }
            mask = mask == null ? null : mask.clone();
        }

        public String ruleLabel() {
            return "B" + birthDigits + "/S" + survivalDigits;
        }

        public boolean[] mask() {
            return mask == null ? null : mask.clone();
        }

        public String maskLabel() {
            boolean[] copy = mask();
            return copy == null ? "000000000" : SeedService.maskToLabel(copy);
        }
    }
}
