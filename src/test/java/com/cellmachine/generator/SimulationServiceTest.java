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
        assertTrue(result.gifBytes().length > 0);
        assertEquals(5, result.stepsRequested());
        assertEquals("B3/S23", result.ruleLabel());
        assertNotNull(result.summary());
    }
}
