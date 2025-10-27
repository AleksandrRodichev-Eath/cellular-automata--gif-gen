package com.cellmachine.generator;

public record SimulationResult(
        byte[] gifBytes,
        String fileName,
        int stepsRequested,
        int stepsSimulated,
        int finalAlive,
        String ruleLabel,
        SimulationDimensions dimensions,
        int delayCs,
        boolean wrap,
        Double requestedDensity,
        Double effectiveDensity,
        String initMaskLabel,
        Integer seedCellCount,
        long randomSeed,
        String summary
) {
    public SimulationResult {
        gifBytes = gifBytes.clone();
    }

    @Override
    public byte[] gifBytes() {
        return gifBytes.clone();
    }
}
