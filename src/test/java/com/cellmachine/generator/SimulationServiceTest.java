package com.cellmachine.generator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SimulationServiceTest {

    private final SimulationService service = new SimulationService();

    @Test
    void runSimulationProducesGifBytes() {
        SimulationOptions options = SimulationOptions.builder()
                .dimensions(new SimulationDimensions(10, 10, 1))
                .steps(5)
                .delayCs(2)
                .wrap(true)
                .build();

        SimulationResult result = service.runSimulation(options);
        assertEquals(SimulationOutputFormat.GIF, result.format());
        assertTrue(result.bytes().length > 0);
        assertEquals(5, result.stepsRequested());
        assertEquals("B3/S23", result.ruleLabel());
        assertNotNull(result.summary());
    }

    @Test
    void runSimulationProducesMp4Bytes() {
        SimulationOptions options = SimulationOptions.builder()
                .dimensions(new SimulationDimensions(8, 8, 1))
                .steps(3)
                .delayCs(5)
                .wrap(false)
                .outputFormat(SimulationOutputFormat.MP4)
                .build();

        SimulationResult result = service.runSimulation(options);
        assertEquals(SimulationOutputFormat.MP4, result.format());
        assertTrue(result.bytes().length > 0);
        assertTrue(result.fileName().endsWith(".mp4"));
        assertEquals(3, result.stepsRequested());
    }
}
