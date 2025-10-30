package com.cellmachine.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final String GIF_DIRECTORY = "gif";
    private static final String LAST_GIF_NAME = "last.gif";

    public static void main(String[] args) {
        SimulationOptions options = SimulationOptions.builder()
            .rule(Rule.parse("B3/S345"))
            .ruleLabel("B3/S12345")
            .build();
        SimulationService simulationService = new SimulationService();
        SimulationResult result = simulationService.runSimulation(options);
        simulationService.persistLastGif(result.gifBytes());
    }

    public SimulationResult runSimulation(SimulationOptions options) {
        Objects.requireNonNull(options, "options");
        SimulationDimensions dimensions = options.dimensions();
        boolean[] mask = options.initMask();
        List<CellCoordinate> seedCells = options.seedCells();
        Double density = options.density();

        String initMaskLabel = mask == null ? null : SeedService.maskToLabel(mask);
        Integer seedCellCount = seedCells.isEmpty() ? null : seedCells.size();

        Grid initialGrid = buildInitialGrid(dimensions, mask, density, seedCells, options.randomSeed());
        SimulationRun run = renderSimulation(options, initialGrid);

        Double effectiveDensity = determineEffectiveDensity(mask, density, seedCells);
        Grid finalGrid = run.finalGrid();
        int finalAlive = finalGrid.aliveCount();

        String baseName = defaultOutputName(options.ruleLabel(), mask, density);
        String fileName = appendStepSuffix(baseName, run.stepsSimulated());

        String seedDescription = describeSeed(initMaskLabel, density, seedCellCount);
        boolean usedRandomness = seedCells.isEmpty() && (mask == null || density != null);
        String summary = buildSummary(
                options.steps(),
                run.stepsSimulated(),
                options.ruleLabel(),
                finalAlive,
                fileName,
                dimensions,
                options.wrap(),
                options.delayCs(),
                seedDescription,
                usedRandomness,
                options.randomSeed());

        return new SimulationResult(
                run.gifBytes(),
                fileName,
                options.steps(),
                run.stepsSimulated(),
                finalAlive,
                options.ruleLabel(),
                dimensions,
                options.delayCs(),
                options.wrap(),
                density,
                effectiveDensity,
                initMaskLabel,
                seedCellCount,
                options.randomSeed(),
                summary);
    }

    public Path persistLastGif(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try {
            Path dir = Path.of(GIF_DIRECTORY);
            Files.createDirectories(dir);
            Path path = dir.resolve(LAST_GIF_NAME);
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return path;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist GIF", ex);
        }
    }

    private Grid buildInitialGrid(
            SimulationDimensions dimensions,
            boolean[] initMask,
            Double density,
            List<CellCoordinate> seedCells,
            long randomSeed) {
        int width = dimensions.width();
        int height = dimensions.height();

        if (!seedCells.isEmpty()) {
            Grid grid = new Grid(width, height);
            for (CellCoordinate coordinate : seedCells) {
                int x = coordinate.x();
                int y = coordinate.y();
                if (x < 0 || x >= width) {
                    throw new IllegalArgumentException("Seed cell x=" + x + " exceeds width " + width);
                }
                if (y < 0 || y >= height) {
                    throw new IllegalArgumentException("Seed cell y=" + y + " exceeds height " + height);
                }
                grid.set(x, y, true);
            }
            return grid;
        }

        if (initMask != null) {
            if (density != null) {
                return SeedService.randomMaskGrid(width, height, initMask, density, randomSeed);
            }
            return SeedService.gridWithCenteredMask(width, height, initMask);
        }

        double effectiveDensity = density != null ? density : SeedService.DEFAULT_RANDOM_DENSITY;
        return SeedService.randomGrid(width, height, effectiveDensity, randomSeed);
    }

    private SimulationRun renderSimulation(SimulationOptions options, Grid initialGrid) {
        Grid current = initialGrid.copy();
        int stepsSimulated = 0;

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             GifWriter writer = new GifWriter(buffer,
                     options.dimensions().width(),
                     options.dimensions().height(),
                     options.dimensions().scale(),
                     options.delayCs())) {

            writer.writeFrame(current);

            for (int step = 0; step < options.steps(); step++) {
                Grid next = Grid.advance(current, options.rule(), options.wrap());
                writer.writeFrame(next);
                stepsSimulated = step + 1;
                if (next.equals(current)) {
                    current = next;
                    break;
                }
                current = next;
            }

            writer.close();
            return new SimulationRun(buffer.toByteArray(), current, stepsSimulated == 0 ? options.steps() : stepsSimulated);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render simulation", ex);
        }
    }

    private Double determineEffectiveDensity(boolean[] mask, Double density, List<CellCoordinate> seedCells) {
        if (!seedCells.isEmpty()) {
            return null;
        }
        if (mask != null) {
            return density;
        }
        return density != null ? density : SeedService.DEFAULT_RANDOM_DENSITY;
    }

    private String defaultOutputName(String rule, boolean[] mask, Double density) {
        String ruleComponent = sanitizeRule(rule);
        List<String> components = new ArrayList<>();
        if (ruleComponent.isEmpty()) {
            components.add("life");
        } else {
            components.add(ruleComponent);
        }
        if (mask != null) {
            components.add(SeedService.maskToLabel(mask));
        }
        if (density != null) {
            components.add(formatDensity(density));
        }
        return String.join("_", components) + ".gif";
    }

    private String describeSeed(String initMaskLabel, Double density, Integer seedCellCount) {
        if (seedCellCount != null) {
            return "manual coordinates (" + seedCellCount + " points)";
        }
        if (initMaskLabel != null) {
            if (density != null) {
                return String.format(Locale.US, "mask %s randomized at %.1f%%", initMaskLabel, density * 100.0);
            }
            return "mask " + initMaskLabel + " centered";
        }
        double effective = density != null ? density : SeedService.DEFAULT_RANDOM_DENSITY;
        return String.format(Locale.US, "random %.1f%% density", effective * 100.0);
    }

    private String buildSummary(
            int stepsRequested,
            int stepsSimulated,
            String ruleLabel,
            int finalAlive,
            String fileName,
            SimulationDimensions dimensions,
            boolean wrap,
            int delayCs,
            String seedDescription,
            boolean usedRandomness,
            long randomSeed) {
        String wrapLabel = wrap ? "toroidal wrap" : "bounded edges";
        StringBuilder builder = new StringBuilder();
        builder.append(String.format(
                Locale.US,
                "Simulated %d generations (requested %d) using rule %s.",
                stepsSimulated,
                stepsRequested,
                ruleLabel));
        builder.append(' ');
        builder.append("Final alive cells: ").append(finalAlive).append('.')
                .append(' ');
        builder.append(String.format(
                Locale.US,
                "Grid %dx%d (scale %d, %s).",
                dimensions.width(),
                dimensions.height(),
                dimensions.scale(),
                wrapLabel));
        builder.append(' ');
        builder.append("Frame delay: ").append(delayCs).append("cs.");
        builder.append(' ');
        builder.append("Seed: ").append(seedDescription).append('.');
        if (usedRandomness) {
            builder.append(' ');
            builder.append("RNG seed: ").append(randomSeed).append('.');
        }
        builder.append(' ');
        builder.append("File tag: ").append(fileName).append('.');
        return builder.toString();
    }

    private String appendStepSuffix(String name, int steps) {
        String suffix = "_" + steps + "s";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(0, dot) + suffix + name.substring(dot);
        }
        return name + suffix;
    }

    private String sanitizeRule(String rule) {
        StringBuilder builder = new StringBuilder();
        for (char ch : rule.trim().toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.toString().replaceAll("^_+|_+$", "");
    }

    private String formatDensity(Double density) {
        double percent = Math.round(density * 100.0);
        percent = Math.max(0.0, Math.min(100.0, percent));
        return Integer.toString((int) percent);
    }

    private record SimulationRun(byte[] gifBytes, Grid finalGrid, int stepsSimulated) {
        SimulationRun {
            gifBytes = gifBytes.clone();
        }

        @Override
        public byte[] gifBytes() {
            return gifBytes.clone();
        }
    }
}
