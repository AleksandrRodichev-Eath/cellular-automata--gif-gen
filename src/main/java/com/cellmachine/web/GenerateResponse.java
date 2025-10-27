package com.cellmachine.web;

public record GenerateResponse(
        String fileName,
        int stepsRequested,
        int stepsSimulated,
        int finalAlive,
        String rule,
        int gridWidth,
        int gridHeight,
        int scale,
        boolean wrap,
        int delayCs,
        Double requestedDensity,
        Double effectiveDensity,
        String initMask,
        Integer seedCellCount,
        long randomSeed,
        String summary,
        String message,
        String lastGifPath
) {
}
