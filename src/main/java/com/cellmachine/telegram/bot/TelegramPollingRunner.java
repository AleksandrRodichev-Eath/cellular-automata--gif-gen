package com.cellmachine.telegram.bot;

import com.cellmachine.telegram.TelegramService;
import com.cellmachine.telegram.dto.TelegramUpdateDto;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class TelegramPollingRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramPollingRunner.class);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    private final TelegramService telegramService;
    private final TelegramBotService botService;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile Long nextOffset;

    public TelegramPollingRunner(TelegramService telegramService,
            TelegramBotService botService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.telegramService = telegramService;
        this.botService = botService;
        this.taskExecutor = taskExecutor;
    }

    @PostConstruct
    public void start() {
        taskExecutor.execute(this::pollLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                TelegramUpdateDto[] updates = telegramService.getUpdates(nextOffset, 30);
                if (updates == null || updates.length == 0) {
                    continue;
                }
                Arrays.stream(updates)
                        .sorted(Comparator.comparingLong(TelegramUpdateDto::updateId))
                        .forEach(update -> {
                            nextOffset = update.updateId() + 1;
                            botService.handleUpdate(update);
                        });
            } catch (Exception ex) {
                log.error("Telegram polling failed", ex);
                sleep(RETRY_DELAY);
            }
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
