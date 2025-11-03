package com.cellmachine.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class SimulationOptionsSerializationTest {

    @Test
    void serializeAndDeserializeRoundTripWithMask() {
        boolean[] mask = SeedService.parseInitMask("101010101");
        SimulationOptions options = SimulationOptions.builder()
                .steps(42)
                .rule(Rule.parse("B36/S23"))
                .ruleLabel("B36/S23")
                .density(0.42)
                .initMask(mask)
                .wrap(false)
                .delayCs(7)
                .dimensions(new SimulationDimensions(50, 40, 2))
                .randomSeed(123456789L)
                .palette(Palette2D.bitbee)
                .build();

        String serialized = options.serialize();
        SimulationOptions restored = SimulationOptions.deserialize(serialized);

        assertEquals(serialized, restored.serialize());
        assertEquals(options.steps(), restored.steps());
        assertEquals(options.rule().label(), restored.rule().label());
        assertEquals(options.ruleLabel(), restored.ruleLabel());
        assertEquals(options.density(), restored.density());
        assertArrayEquals(options.initMask(), restored.initMask());
        assertEquals(options.seedCells(), restored.seedCells());
        assertEquals(options.wrap(), restored.wrap());
        assertEquals(options.delayCs(), restored.delayCs());
        assertEquals(options.dimensions(), restored.dimensions());
        assertEquals(options.randomSeed(), restored.randomSeed());
        assertEquals(SimulationOutputFormat.MP4, restored.outputFormat());
        assertEquals(options.palette(), restored.palette());
    }

    @Test
    void serializeAndDeserializeRoundTripWithSeedCells() {
        SimulationOptions options = SimulationOptions.builder()
                .steps(15)
                .rule(Rule.parse("B2/S23"))
                .ruleLabel("CustomLabel")
                .seedCells(List.of(new CellCoordinate(1, 2), new CellCoordinate(3, 4)))
                .wrap(true)
                .delayCs(3)
                .dimensions(new SimulationDimensions(30, 20, 1))
                .randomSeed(987654321L)
                .outputFormat(SimulationOutputFormat.MP4)
                .palette(Palette2D.cgaPastel)
                .build();

        String serialized = options.serialize();
        SimulationOptions restored = SimulationOptions.deserialize(serialized);

        assertEquals(serialized, restored.serialize());
        assertEquals(options.steps(), restored.steps());
        assertEquals(options.rule().label(), restored.rule().label());
        assertEquals(options.ruleLabel(), restored.ruleLabel());
        assertEquals(options.density(), restored.density());
        assertEquals(options.seedCells(), restored.seedCells());
        assertEquals(options.wrap(), restored.wrap());
        assertEquals(options.delayCs(), restored.delayCs());
        assertEquals(options.dimensions(), restored.dimensions());
        assertEquals(options.randomSeed(), restored.randomSeed());
        assertEquals(options.outputFormat(), restored.outputFormat());
        assertEquals(options.palette(), restored.palette());
    }
}
