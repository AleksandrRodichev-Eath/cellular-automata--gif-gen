package com.cellmachine.scheduler;

import com.cellmachine.generator.Rule;
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
    private static final String SCHEDULE_RULE_CODE = "B3/S12345";

    private final SimulationService simulationService;
    private final TelegramService telegramService;

    public DailyScheduler(SimulationService simulationService, TelegramService telegramService) {
        this.simulationService = simulationService;
        this.telegramService = telegramService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sendStartupSnapshot() {
        dispatchGif("startup");
    }

    @Scheduled(cron = "0 0 10,18 * * *", zone = "Asia/Tbilisi")
    public void sendScheduledGif() {
        dispatchGif("scheduled");
    }

    private void dispatchGif(String label) {
        try {
            SimulationOptions options = SimulationOptions.builder()
                    .rule(Rule.parse(SCHEDULE_RULE_CODE))
                    .ruleLabel(SCHEDULE_RULE_CODE)
                    .build();
            SimulationResult result = simulationService.runSimulation(options);
            Path savedPath = simulationService.persistLastMedia(result.bytes(), result.format());
            telegramService.sendAnimation(result.fileName(), result.bytes(), result.summary());
            log.info("Dispatched {} {}: {} (saved at {})", label, result.format(), result.summary(), savedPath);
        } catch (Exception ex) {
            log.error("Failed to dispatch {} animation", label, ex);
        }
    }
}
