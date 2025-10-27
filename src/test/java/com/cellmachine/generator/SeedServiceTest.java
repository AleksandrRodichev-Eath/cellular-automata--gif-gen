package com.cellmachine.generator;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsCoordinatesFromFile() throws IOException {
        Path file = tempDir.resolve("seed.txt");
        Files.writeString(file, "1 2\n# comment\n3 4\n");
        Grid grid = SeedService.loadSeedFromFile(file, 5, 5);
        assertTrue(grid.get(1, 2));
        assertTrue(grid.get(3, 4));
        assertEquals(2, grid.aliveCount());
    }

    @Test
    void rejectsOutOfBoundsCoordinates() throws IOException {
        Path file = tempDir.resolve("seed_invalid.txt");
        Files.writeString(file, "5 0\n");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SeedService.loadSeedFromFile(file, 5, 5));
        assertTrue(ex.getMessage().contains("outside"));
    }

    @Test
    void randomGridRespectsDensity() {
        Grid grid = SeedService.randomGrid(10, 10, 0.5, 42);
        int alive = grid.aliveCount();
        assertTrue(alive > 0);
        assertTrue(alive < 100);
    }

    @Test
    void parseInitMaskAcceptsBinaryString() {
        boolean[] mask = SeedService.parseInitMask("111101111");
        long count = 0;
        for (boolean cell : mask) {
            if (cell) {
                count++;
            }
        }
        assertEquals(8, count);
    }

    @Test
    void parseInitMaskRejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> SeedService.parseInitMask("1010"));
    }

    @Test
    void maskToLabelReturnsBinaryString() {
        boolean[] mask = SeedService.parseInitMask("101010101");
        assertEquals("101010101", SeedService.maskToLabel(mask));
    }
}
