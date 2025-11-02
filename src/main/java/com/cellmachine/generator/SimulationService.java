package com.cellmachine.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

    private static final String GIF_DIRECTORY = "gif";
    private static final String LAST_GIF_NAME = "last.gif";
    private static final String MP4_DIRECTORY = "video";
    private static final String LAST_MP4_NAME = "last.mp4";
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    public static void main(String[] args) {
        SimulationOptions options = SimulationOptions.builder()
            .rule(Rule.parse("B3/S345"))
            .ruleLabel("B3/S12345")
            .outputFormat(SimulationOutputFormat.MP4)
            .build();
        SimulationService simulationService = new SimulationService();
        SimulationResult result = simulationService.runSimulation(options);
        simulationService.persistLastMedia(result.bytes(), result.format());
    }

    public SimulationResult runSimulation(SimulationOptions options) {
        Objects.requireNonNull(options, "options");
        long start = System.nanoTime();
        SimulationDimensions dimensions = options.dimensions();
        boolean[] mask = options.initMask();
        List<CellCoordinate> seedCells = options.seedCells();
        Double density = options.density();
        SimulationOutputFormat format = options.outputFormat();
        Palette2D palette = options.palette();

        String initMaskLabel = mask == null ? null : SeedService.maskToLabel(mask);
        Integer seedCellCount = seedCells.isEmpty() ? null : seedCells.size();

        Grid initialGrid = buildInitialGrid(dimensions, mask, density, seedCells, options.randomSeed());
        SimulationRun run = renderSimulation(options, initialGrid, palette);

        Double effectiveDensity = determineEffectiveDensity(mask, density, seedCells);
        Grid finalGrid = run.finalGrid();
        int finalAlive = finalGrid.aliveCount();

        String baseName = defaultOutputName(options.ruleLabel(), mask, density, format);
        String fileName = appendStepSuffix(baseName, run.stepsSimulated());

        String summary = buildSummary(options);

        SimulationResult simulationResult = new SimulationResult(
                run.bytes(),
                fileName,
                format,
                palette,
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
        Duration spent = Duration.ofNanos(System.nanoTime() - start);
        long sizeBytes = simulationResult.bytes().length;
        double sizeKb = sizeBytes / 1024.0;
        double seconds = spent.toNanos() / 1_000_000_000.0;
        String sizeLabel = String.format(Locale.US, "%.1f KB", sizeKb);
        String timeLabel = String.format(Locale.US, "%.1f s", seconds);
        log.info(
                "Simulation {} {}: {} (size={}, spent={})",
                simulationResult.fileName(),
                simulationResult.format(),
                summary,
                sizeLabel,
                timeLabel);
        return simulationResult;
    }

    public Path persistLastMedia(byte[] bytes, SimulationOutputFormat format) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(format, "format");
        try {
            return switch (format) {
                case GIF -> persist(bytes, GIF_DIRECTORY, LAST_GIF_NAME);
                case MP4 -> persist(bytes, MP4_DIRECTORY, LAST_MP4_NAME);
            };
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist " + format.name() + " output", ex);
        }
    }

    private Path persist(byte[] bytes, String directory, String fileName) throws IOException {
        Path dir = Path.of(directory);
        Files.createDirectories(dir);
        Path path = dir.resolve(fileName);
        Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return path;
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

    private SimulationRun renderSimulation(SimulationOptions options, Grid initialGrid, Palette2D palette) {
        try {
            return switch (options.outputFormat()) {
                case GIF -> renderGif(options, initialGrid, palette);
                case MP4 -> renderMp4(options, initialGrid, palette);
            };
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render simulation", ex);
        }
    }

    private SimulationRun renderGif(SimulationOptions options, Grid initialGrid, Palette2D palette) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             GifWriter writer = new GifWriter(buffer,
                     options.dimensions().width(),
                     options.dimensions().height(),
                     options.dimensions().scale(),
                     options.delayCs(),
                     palette)) {
            SimulationLoopResult loop = writeFrames(initialGrid, options, writer::writeFrame);
            writer.close();
            return new SimulationRun(buffer.toByteArray(), loop.finalGrid(), loop.stepsSimulated());
        }
    }

    private SimulationRun renderMp4(SimulationOptions options, Grid initialGrid, Palette2D palette) throws IOException {
        try (Mp4Writer writer = new Mp4Writer(
                options.dimensions().width(),
                options.dimensions().height(),
                options.dimensions().scale(),
                options.delayCs(),
                palette)) {
            SimulationLoopResult loop = writeFrames(initialGrid, options, writer::writeFrame);
            writer.close();
            return new SimulationRun(writer.toByteArray(), loop.finalGrid(), loop.stepsSimulated());
        }
    }

    private SimulationLoopResult writeFrames(Grid initialGrid, SimulationOptions options, FrameConsumer frameConsumer) throws IOException {
        Grid current = initialGrid.copy();
        frameConsumer.writeFrame(current);
        int framesRendered = 1;
        ProgressLogger progressLogger = ProgressLogger.create(options.progressLogPercentStep(), options.steps() + 1);
        if (progressLogger != null) {
            progressLogger.record(framesRendered);
        }
        int stepsSimulated = 0;
        for (int step = 0; step < options.steps(); step++) {
            Grid next = Grid.advance(current, options.rule(), options.wrap());
            frameConsumer.writeFrame(next);
            framesRendered++;
            if (progressLogger != null) {
                progressLogger.record(framesRendered);
            }
            stepsSimulated = step + 1;
            if (next.equals(current)) {
                current = next;
                break;
            }
            current = next;
        }
        if (stepsSimulated == 0) {
            stepsSimulated = options.steps();
        }
        return new SimulationLoopResult(current, stepsSimulated);
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

    private String defaultOutputName(String rule, boolean[] mask, Double density, SimulationOutputFormat format) {
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
        String base = String.join("_", components);
        String extension = format.fileExtension();
        return base + "." + extension;
    }

    private String buildSummary(SimulationOptions options) {
        return options.serialize();
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

    private static final class ProgressLogger {
        private final int totalFrames;
        private final int stepPercent;
        private int nextPercent;
        private int lastLoggedPercent;

        private ProgressLogger(int totalFrames, int stepPercent) {
            this.totalFrames = Math.max(totalFrames, 1);
            this.stepPercent = stepPercent;
            this.nextPercent = stepPercent;
            this.lastLoggedPercent = 0;
        }

        static ProgressLogger create(Integer requestedPercentStep, int totalFrames) {
            if (requestedPercentStep == null) {
                return null;
            }
            return new ProgressLogger(totalFrames, requestedPercentStep);
        }

        void record(int framesRendered) {
            if (totalFrames <= 0) {
                return;
            }
            int percent = (int) Math.floor(framesRendered * 100.0 / totalFrames);
            percent = Math.min(percent, 100);
            while (nextPercent <= 100 && percent >= nextPercent) {
                log.info("Rendered {}% of frames ({}/{}).", nextPercent, framesRendered, totalFrames);
                lastLoggedPercent = nextPercent;
                nextPercent += stepPercent;
            }
            if (percent == 100 && lastLoggedPercent < 100) {
                log.info("Rendered 100% of frames ({}/{}).", framesRendered, totalFrames);
                lastLoggedPercent = 100;
            }
        }
    }

    @FunctionalInterface
    private interface FrameConsumer {
        void writeFrame(Grid grid) throws IOException;
    }

    private record SimulationLoopResult(Grid finalGrid, int stepsSimulated) {
    }

    private record SimulationRun(byte[] bytes, Grid finalGrid, int stepsSimulated) {
        SimulationRun {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
