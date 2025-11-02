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
                .palette(Palette2D.ysConcreteJungle)
                .outputFormat(SimulationOutputFormat.GIF)
                .build();

        SimulationResult result = service.runSimulation(options);
        assertEquals(SimulationOutputFormat.GIF, result.format());
        assertTrue(result.bytes().length > 0);
        assertEquals(5, result.stepsRequested());
        assertEquals("B3/S23", result.ruleLabel());
        assertNotNull(result.summary());
        assertEquals(Palette2D.ysConcreteJungle, result.palette());
        assertEquals(options.serialize(), result.summary());
    }

    @Test
    void runSimulationProducesMp4Bytes() {
        SimulationOptions options = SimulationOptions.builder()
                .dimensions(new SimulationDimensions(8, 8, 1))
                .steps(3)
                .delayCs(5)
                .wrap(false)
                .palette(Palette2D.bitbee)
                .outputFormat(SimulationOutputFormat.MP4)
                .build();

        SimulationResult result = service.runSimulation(options);
        assertEquals(SimulationOutputFormat.MP4, result.format());
        assertEquals(Palette2D.bitbee, result.palette());
        assertTrue(result.bytes().length > 0);
        assertTrue(result.fileName().endsWith(".mp4"));
        assertEquals(3, result.stepsRequested());
        assertEquals(options.serialize(), result.summary());
    }
}
