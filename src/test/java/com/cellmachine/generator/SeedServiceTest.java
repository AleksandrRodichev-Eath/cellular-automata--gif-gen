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
    void parseInitMaskAcceptsVariableSizes() {
        boolean[] mask4x4 = SeedService.parseInitMask("1".repeat(16));
        assertEquals(16, mask4x4.length);
        boolean[] mask5x5 = SeedService.parseInitMask("0".repeat(24) + "1");
        assertEquals(25, mask5x5.length);
        assertEquals("1".repeat(16), SeedService.maskToLabel(mask4x4));
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

    @Test
    void gridWithCenteredMaskSupportsLargerMasks() {
        boolean[] mask = SeedService.parseInitMask("1".repeat(16));
        Grid grid = SeedService.gridWithCenteredMask(10, 10, mask);
        assertEquals(16, grid.aliveCount());
        int base = (10 - 4) / 2;
        assertTrue(grid.get(base, base));
        assertTrue(grid.get(base + 3, base + 3));
    }

    @Test
    void gridWithCenteredMaskRejectsTooSmallGridForLargeMask() {
        boolean[] mask = SeedService.parseInitMask("1".repeat(25));
        assertThrows(IllegalArgumentException.class, () -> SeedService.gridWithCenteredMask(4, 6, mask));
    }
}
