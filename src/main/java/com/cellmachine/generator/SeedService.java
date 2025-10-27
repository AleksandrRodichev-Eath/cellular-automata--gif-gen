package com.cellmachine.generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public final class SeedService {

    public static final double DEFAULT_RANDOM_DENSITY = 0.15d;
    public static final long DEFAULT_RANDOM_SEED = 0x5EED5EEDL;

    private SeedService() {
    }

    public static Grid loadSeedFromFile(Path path, int width, int height) throws IOException {
        Objects.requireNonNull(path, "path");
        ensurePositiveDimensions(width, height);
        Grid grid = new Grid(width, height);
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Line " + lineNo + " must contain two integers: '" + trimmed + "'");
                }
                int x = parseCoordinate(parts[0], lineNo, 'x');
                int y = parseCoordinate(parts[1], lineNo, 'y');
                if (x < 0 || x >= width || y < 0 || y >= height) {
                    throw new IllegalArgumentException("Coordinate (" + x + ", " + y + ") on line " + lineNo + " is outside the " + width + "x" + height + " grid");
                }
                grid.set(x, y, true);
            }
        }
        return grid;
    }

    public static Grid randomGrid(int width, int height, double density, long seed) {
        ensurePositiveDimensions(width, height);
        ensureDensity(density);
        Grid grid = new Grid(width, height);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (random.nextDouble() < density) {
                    grid.set(x, y, true);
                }
            }
        }
        return grid;
    }

    public static boolean[] parseInitMask(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Init mask must contain exactly 9 entries (3x3 matrix)");
        }
        List<Boolean> values = new ArrayList<>(9);
        for (char ch : raw.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                continue;
            }
            if (ch == '0') {
                values.add(Boolean.FALSE);
            } else if (ch == '1') {
                values.add(Boolean.TRUE);
            } else {
                throw new IllegalArgumentException("Init mask must contain only '0' or '1' characters");
            }
        }
        if (values.size() != 9) {
            throw new IllegalArgumentException("Init mask must contain exactly 9 entries (3x3 matrix)");
        }
        boolean[] mask = new boolean[9];
        for (int i = 0; i < values.size(); i++) {
            mask[i] = values.get(i);
        }
        return mask;
    }

    public static Grid gridWithCenteredMask(int width, int height, boolean[] mask) {
        ensureMask(mask);
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Grid must be at least 3x3 for init mask");
        }
        Grid grid = new Grid(width, height);
        int baseX = (width - 3) / 2;
        int baseY = (height - 3) / 2;
        applyMask(grid, baseX, baseY, mask);
        return grid;
    }

    public static Grid randomMaskGrid(int width, int height, boolean[] mask, double density, long seed) {
        ensureMask(mask);
        ensurePositiveDimensions(width, height);
        ensureDensity(density);
        if (width < 3 || height < 3) {
            throw new IllegalArgumentException("Grid must be at least 3x3 for init mask");
        }

        Grid grid = new Grid(width, height);
        int activeCells = 0;
        for (boolean active : mask) {
            if (active) {
                activeCells++;
            }
        }
        if (density == 0.0 || activeCells == 0) {
            return grid;
        }

        int targetAlive = (int) Math.round(width * height * density);
        if (targetAlive == 0) {
            return grid;
        }

        int shapesNeeded = (targetAlive + activeCells - 1) / activeCells;
        int maxAttempts = shapesNeeded * 10 + 100;
        Random random = new Random(seed);
        int maxX = width - 3;
        int maxY = height - 3;
        List<int[]> offsets = maskOffsets(mask);

        int attempts = 0;
        while (grid.aliveCount() < targetAlive && attempts < maxAttempts) {
            int x0 = maxX == 0 ? 0 : random.nextInt(maxX + 1);
            int y0 = maxY == 0 ? 0 : random.nextInt(maxY + 1);
            for (int[] offset : offsets) {
                grid.set(x0 + offset[0], y0 + offset[1], true);
            }
            attempts++;
        }
        return grid;
    }

    public static String maskToLabel(boolean[] mask) {
        ensureMask(mask);
        StringBuilder builder = new StringBuilder(mask.length);
        for (boolean active : mask) {
            builder.append(active ? '1' : '0');
        }
        return builder.toString();
    }

    private static void ensurePositiveDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive");
        }
    }

    private static void ensureDensity(double density) {
        if (Double.isNaN(density) || density < 0.0 || density > 1.0) {
            throw new IllegalArgumentException("Density must be between 0.0 and 1.0 inclusive");
        }
    }

    private static void ensureMask(boolean[] mask) {
        if (mask == null || mask.length != 9) {
            throw new IllegalArgumentException("Init mask must contain exactly 9 entries (3x3 matrix)");
        }
    }

    private static int parseCoordinate(String raw, int lineNo, char axis) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + axis + " coordinate '" + raw + "' on line " + lineNo);
        }
    }

    private static void applyMask(Grid grid, int baseX, int baseY, boolean[] mask) {
        for (int idx = 0; idx < mask.length; idx++) {
            if (!mask[idx]) {
                continue;
            }
            int dx = idx % 3;
            int dy = idx / 3;
            grid.set(baseX + dx, baseY + dy, true);
        }
    }

    private static List<int[]> maskOffsets(boolean[] mask) {
        List<int[]> offsets = new ArrayList<>();
        for (int idx = 0; idx < mask.length; idx++) {
            if (mask[idx]) {
                offsets.add(new int[]{idx % 3, idx / 3});
            }
        }
        return offsets;
    }
}
