package com.cellmachine.web;

import java.util.List;

public record GenerateRequest(
        Integer steps,
        String rule,
        Double density,
        String initMask,
        List<SeedCell> seedCells,
        Boolean wrap,
        Integer delay,
        Integer width,
        Integer height,
        Integer scale,
        String caption,
        Long randomSeed,
        String format
) {
}
