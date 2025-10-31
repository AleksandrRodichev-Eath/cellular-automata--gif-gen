package com.cellmachine.web;

import com.cellmachine.generator.CellCoordinate;
import com.cellmachine.generator.Rule;
import com.cellmachine.generator.SimulationDimensions;
import com.cellmachine.generator.SimulationOptions;
import com.cellmachine.generator.SimulationOutputFormat;
import com.cellmachine.generator.SimulationResult;
import com.cellmachine.generator.SimulationService;
import com.cellmachine.generator.SeedService;
import com.cellmachine.telegram.TelegramService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class SimulationController {

    private static final Logger log = LoggerFactory.getLogger(SimulationController.class);
    private static final Path LAST_GIF_PATH = Path.of("gif", "last.gif");

    private final SimulationService simulationService;
    private final TelegramService telegramService;

    public SimulationController(SimulationService simulationService, TelegramService telegramService) {
        this.simulationService = simulationService;
        this.telegramService = telegramService;
    }

    @PostMapping(path = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public GenerateResponse generate(@RequestBody GenerateRequest request) {
        String ruleLabel = StringUtils.hasText(request.rule()) ? request.rule().trim() : "B3/S23";
        Rule rule = parseRule(ruleLabel);
        boolean[] initMask = parseInitMask(request.initMask());
        List<CellCoordinate> seedCoordinates = mapSeedCells(request.seedCells());

        int width = request.width() != null ? request.width() : SimulationDimensions.DEFAULT_WORLD_WIDTH;
        int height = request.height() != null ? request.height() : SimulationDimensions.DEFAULT_WORLD_HEIGHT;
        int scale = request.scale() != null ? request.scale() : SimulationDimensions.DEFAULT_SCALE;
        SimulationDimensions dimensions;
        try {
            dimensions = new SimulationDimensions(width, height, scale);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        SimulationOutputFormat format = parseFormat(request.format());

        SimulationOptions options;
        try {
            options = buildOptions(request, ruleLabel, rule, initMask, seedCoordinates, dimensions, format);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        SimulationResult result;
        try {
            result = simulationService.runSimulation(options);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Simulation failed: " + ex.getMessage(), ex);
        }

        byte[] mediaBytes = result.bytes();
        Path savedPath;
        try {
            savedPath = simulationService.persistLastMedia(mediaBytes, result.format());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }

        String message = buildMessage(result.summary(), request.caption());
        try {
            telegramService.sendAnimation(result.fileName(), mediaBytes, message);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }

        log.info("Generated {} {} (steps {} -> {} alive {})",
                result.format(),
                result.fileName(),
                result.stepsSimulated(),
                result.ruleLabel(),
                result.finalAlive());

        return new GenerateResponse(
                result.fileName(),
                result.format().name(),
                result.mediaType(),
                result.stepsRequested(),
                result.stepsSimulated(),
                result.finalAlive(),
                result.ruleLabel(),
                result.dimensions().width(),
                result.dimensions().height(),
                result.dimensions().scale(),
                result.wrap(),
                result.delayCs(),
                result.requestedDensity(),
                result.effectiveDensity(),
                result.initMaskLabel(),
                result.seedCellCount(),
                result.randomSeed(),
                result.summary(),
                message,
                savedPath.toString());
    }

    @GetMapping(path = "/last.gif")
    public ResponseEntity<byte[]> getLastGif() {
        if (!Files.exists(LAST_GIF_PATH)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No GIF generated yet");
        }
        try {
            byte[] bytes = Files.readAllBytes(LAST_GIF_PATH);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_GIF_VALUE)
                    .body(bytes);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read GIF", ex);
        }
    }

    private Rule parseRule(String ruleLabel) {
        try {
            return Rule.parse(ruleLabel);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid rule: " + ex.getMessage(), ex);
        }
    }

    private boolean[] parseInitMask(String initMask) {
        if (!StringUtils.hasText(initMask)) {
            return null;
        }
        try {
            return SeedService.parseInitMask(initMask);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private List<CellCoordinate> mapSeedCells(List<SeedCell> seedCells) {
        if (seedCells == null || seedCells.isEmpty()) {
            return List.of();
        }
        List<CellCoordinate> coordinates = new ArrayList<>(seedCells.size());
        for (SeedCell cell : seedCells) {
            coordinates.add(new CellCoordinate(cell.x(), cell.y()));
        }
        return coordinates;
    }

    private SimulationOutputFormat parseFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return SimulationOutputFormat.GIF;
        }
        String normalized = format.trim().toUpperCase(Locale.ROOT);
        try {
            return SimulationOutputFormat.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported format: " + format, ex);
        }
    }

    private SimulationOptions buildOptions(
            GenerateRequest request,
            String ruleLabel,
            Rule rule,
            boolean[] initMask,
            List<CellCoordinate> seedCells,
            SimulationDimensions dimensions,
            SimulationOutputFormat format) {
        SimulationOptions.Builder builder = SimulationOptions.builder()
                .rule(rule)
                .ruleLabel(ruleLabel)
                .dimensions(dimensions)
                .wrap(request.wrap() == null || request.wrap())
                .delayCs(request.delay() != null ? request.delay() : SimulationOptions.DEFAULT_DELAY_CS)
                .randomSeed(request.randomSeed() != null ? request.randomSeed() : SeedService.DEFAULT_RANDOM_SEED)
                .outputFormat(format);

        if (request.steps() != null) {
            builder.steps(request.steps());
        }
        if (request.density() != null) {
            builder.density(request.density());
        }
        if (initMask != null) {
            builder.initMask(initMask);
        }
        if (!seedCells.isEmpty()) {
            builder.seedCells(seedCells);
        }
        return builder.build();
    }

    private String buildMessage(String summary, String caption) {
        if (!StringUtils.hasText(caption)) {
            return summary;
        }
        String trimmed = caption.trim();
        return summary + "\n\n" + trimmed;
    }
}
