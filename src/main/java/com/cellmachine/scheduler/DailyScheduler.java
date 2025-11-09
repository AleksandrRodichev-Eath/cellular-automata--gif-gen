package com.cellmachine.scheduler;

import com.cellmachine.generator.RandomSimulationFactory;
import com.cellmachine.generator.RandomSimulationFactory.RandomSelection;
import com.cellmachine.generator.SimulationOptions;
import com.cellmachine.generator.SimulationResult;
import com.cellmachine.generator.SimulationService;
import com.cellmachine.telegram.TelegramService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyScheduler.class);
    private static final int RANDOM_BATCH_SIZE = 3;

    private final SimulationService simulationService;
    private final TelegramService telegramService;

    public DailyScheduler(SimulationService simulationService, TelegramService telegramService) {
        this.simulationService = simulationService;
        this.telegramService = telegramService;
    }

    @Scheduled(cron = "0 0 10,18 * * *", zone = "Asia/Tbilisi")
    public void sendScheduledGif() {
        dispatchBatch("scheduled");
    }

    private void dispatchBatch(String label) {
        dispatchRandomAnimation(label);
        dispatchRandomAnimation(label);
        dispatchRandomMaskAnimation(label);
        dispatchRandomMaskAnimation(label);
    }

    private void dispatchRandomMaskAnimation(String label) {
        RandomSelection selection = RandomSimulationFactory.create(true);
        SimulationOptions options = RandomSimulationFactory.buildOptions(selection);
        try {
            SimulationResult result = simulationService.runSimulation(options);
            Path savedPath = simulationService.persistLastMedia(result.bytes(), result.format());
            telegramService.sendAnimation(result.fileName(), result.bytes(), result.summary());
            log.info(
                "Dispatched {} animation: {} (rule={}, mask={}) saved at {}",
                label,
                result.format(),
                selection.ruleLabel(),
                selection.maskLabel(),
                savedPath);
        } catch (Exception ex) {
            log.error("Failed to dispatch {} animation", label, ex);
        }
    }

    private void dispatchRandomAnimation(String label) {
        RandomSelection selection = RandomSimulationFactory.create(false);
        SimulationOptions options = RandomSimulationFactory.buildOptions(selection);
        try {
            SimulationResult result = simulationService.runSimulation(options);
            Path savedPath = simulationService.persistLastMedia(result.bytes(), result.format());
            telegramService.sendAnimation(result.fileName(), result.bytes(), result.summary());
            log.info(
                    "Dispatched {} animation: {} (rule={}, mask={}) saved at {}",
                    label,
                    result.format(),
                    selection.ruleLabel(),
                    selection.maskLabel(),
                    savedPath);
        } catch (Exception ex) {
            log.error("Failed to dispatch {} animation", label, ex);
        }
    }
}
