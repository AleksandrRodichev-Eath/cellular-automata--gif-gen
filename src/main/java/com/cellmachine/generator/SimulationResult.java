package com.cellmachine.generator;

public record SimulationResult(
        byte[] bytes,
        String fileName,
        SimulationOutputFormat format,
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
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    public String mediaType() {
        return format.mediaType();
    }
}
