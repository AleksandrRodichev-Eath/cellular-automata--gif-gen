package com.cellmachine.generator;

public record SimulationDimensions(int width, int height, int scale) {

    public static final int DEFAULT_WORLD_WIDTH = 200;
    public static final int DEFAULT_WORLD_HEIGHT = 200;
    public static final int DEFAULT_SCALE = 4;

    public SimulationDimensions {
        if (width <= 0 || height <= 0 || scale <= 0) {
            throw new IllegalArgumentException("Dimensions and scale must be positive");
        }
    }

    public static SimulationDimensions defaults() {
        return new SimulationDimensions(DEFAULT_WORLD_WIDTH, DEFAULT_WORLD_HEIGHT, DEFAULT_SCALE);
    }
}
