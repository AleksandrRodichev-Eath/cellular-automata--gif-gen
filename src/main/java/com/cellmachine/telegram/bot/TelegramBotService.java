package com.cellmachine.telegram.bot;

import com.cellmachine.generator.Palette2D;
import com.cellmachine.generator.Rule;
import com.cellmachine.generator.SeedService;
import com.cellmachine.generator.SimulationDimensions;
import com.cellmachine.generator.SimulationOptions;
import com.cellmachine.generator.SimulationOutputFormat;
import com.cellmachine.generator.SimulationResult;
import com.cellmachine.generator.SimulationService;
import com.cellmachine.telegram.TelegramService;
import com.cellmachine.telegram.dto.InlineKeyboardButtonDto;
import com.cellmachine.telegram.dto.InlineKeyboardMarkupDto;
import com.cellmachine.telegram.dto.TelegramCallbackQueryDto;
import com.cellmachine.telegram.dto.TelegramMessageDto;
import com.cellmachine.telegram.dto.TelegramUpdateDto;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TelegramBotService {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private static final Pattern RULE_PART_PATTERN = Pattern.compile("^[0-8]{1,8}$");
    private static final Duration SESSION_TIMEOUT = Duration.ofHours(1);

    private final SimulationService simulationService;
    private final TelegramService telegramService;
    private final TaskExecutor taskExecutor;
    private final ConcurrentMap<Long, ChatSession> sessions = new ConcurrentHashMap<>();

    public TelegramBotService(SimulationService simulationService, TelegramService telegramService,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.simulationService = simulationService;
        this.telegramService = telegramService;
        this.taskExecutor = taskExecutor;
    }

    public void handleUpdate(TelegramUpdateDto update) {
        if (update == null) {
            return;
        }
        if (update.message() != null) {
            handleMessage(update.message());
        } else if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
        }
    }

    private void handleMessage(TelegramMessageDto message) {
        if (message.chat() == null || message.chat().id() == 0) {
            return;
        }
        long chatId = message.chat().id();
        String text = message.text();
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if (trimmed.startsWith("/start")) {
            startConversation(chatId);
            return;
        }

        ChatSession session = sessions.get(chatId);
        if (session == null) {
            telegramService.sendMessage(chatId, "Отправьте /start, чтобы начать настройку симуляции.");
            return;
        }

        switch (session.step()) {
            case WAITING_FOR_B -> processBirthInput(session, trimmed);
            case WAITING_FOR_S -> processSurvivalInput(session, trimmed);
            case WAITING_FOR_MANUAL_STEPS -> processManualSteps(session, trimmed);
            case WAITING_FOR_MANUAL_DENSITY -> processManualDensity(session, trimmed);
            case WAITING_FOR_MASK_INPUT -> processMaskInput(session, trimmed);
            case GENERATING -> telegramService.sendMessage(chatId, "Видео уже генерируется, пожалуйста подождите...");
            default -> telegramService.sendMessage(chatId, "Пожалуйста, выберите вариант с кнопок.");
        }
    }

    private void handleCallback(TelegramCallbackQueryDto callback) {
        if (callback.message() == null || callback.message().chat() == null) {
            return;
        }
        long chatId = callback.message().chat().id();
        ChatSession session = sessions.get(chatId);
        if (session == null) {
            telegramService.answerCallback(callback.id(), "Сессия неактивна. Отправьте /start.");
            tryRemoveKeyboard(callback.message());
            return;
        }

        String data = callback.data();
        if (data == null) {
            telegramService.answerCallback(callback.id(), null);
            return;
        }

        try {
            switch (session.step()) {
                case CHOOSING_SIZE -> processSizeChoice(session, callback, data);
                case CHOOSING_STEPS -> processStepsChoice(session, callback, data);
                case CHOOSING_DENSITY -> processDensityChoice(session, callback, data);
                case CHOOSING_MASK_ACTION -> processMaskChoice(session, callback, data);
                case CHOOSING_WRAP -> processWrapChoice(session, callback, data);
                case CHOOSING_PALETTE -> processPaletteChoice(session, callback, data);
                default -> telegramService.answerCallback(callback.id(), "Сейчас ожидается текстовый ввод.");
            }
        } finally {
            telegramService.answerCallback(callback.id(), null);
        }
    }

    private void startConversation(long chatId) {
        ChatSession existing = sessions.remove(chatId);
        if (existing != null) {
            removeKeyboard(existing);
        }
        ChatSession session = new ChatSession(chatId);
        sessions.put(chatId, session);
        telegramService.sendMessage(chatId, "Привет! Давай соберём параметры симуляции Life.");
        telegramService.sendMessage(chatId, "Введите цифры для части B (0–8, без пробелов):");
    }

    private void processBirthInput(ChatSession session, String value) {
        if (!RULE_PART_PATTERN.matcher(value).matches()) {
            telegramService.sendMessage(session.chatId(), "Нужны только цифры 0–8, например 3 или 367. Попробуйте ещё раз.");
            return;
        }
        session.birthDigits(value);
        session.step(ConversationStep.WAITING_FOR_S);
        telegramService.sendMessage(session.chatId(), "Теперь введите цифры для части S (0–8):");
    }

    private void processSurvivalInput(ChatSession session, String value) {
        if (!RULE_PART_PATTERN.matcher(value).matches()) {
            telegramService.sendMessage(session.chatId(), "Нужны только цифры 0–8, например 23 или 345. Попробуйте ещё раз.");
            return;
        }
        session.survivalDigits(value);
        promptGridSize(session);
    }

    private void processManualSteps(ChatSession session, String value) {
        int steps;
        try {
            steps = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            telegramService.sendMessage(session.chatId(), "Введите целое число от 1 до 1000.");
            return;
        }
        if (steps <= 0 || steps > 1000) {
            telegramService.sendMessage(session.chatId(), "Шаги должны быть в пределах 1–1000.");
            return;
        }
        session.steps(steps);
        promptDensity(session);
    }

    private void processManualDensity(ChatSession session, String value) {
        double density;
        try {
            density = Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            telegramService.sendMessage(session.chatId(), "Введите число от 0 до 1 (например 0.12).");
            return;
        }
        if (density < 0.0 || density > 1.0 || Double.isNaN(density)) {
            telegramService.sendMessage(session.chatId(), "Плотность должна быть между 0 и 1.");
            return;
        }
        session.density(density);
        promptMaskAction(session);
    }

    private void processMaskInput(ChatSession session, String value) {
        try {
            boolean[] mask = SeedService.parseInitMask(value);
            session.mask(mask);
        } catch (IllegalArgumentException ex) {
            telegramService.sendMessage(session.chatId(), "Маска: 9 символов 0 или 1 (например 111000111). Ошибка: " + ex.getMessage());
            return;
        }
        promptWrap(session);
    }

    private void processSizeChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        Map<String, int[]> sizes = Map.of(
                "SIZE_100", new int[]{100, 100},
                "SIZE_200", new int[]{200, 200},
                "SIZE_400", new int[]{400, 400}
        );
        int[] dims = sizes.get(data);
        if (dims == null) {
            telegramService.sendMessage(session.chatId(), "Неизвестный размер поля.");
            return;
        }
        session.dimensions(dims[0], dims[1]);
        removeKeyboard(callback.message());
        session.clearLastPromptMessage();
        promptSteps(session);
    }

    private void processStepsChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        if ("STEPS_MANUAL".equals(data)) {
            session.step(ConversationStep.WAITING_FOR_MANUAL_STEPS);
            removeKeyboard(callback.message());
            session.clearLastPromptMessage();
            telegramService.sendMessage(session.chatId(), "Введите количество шагов (1–1000):");
            return;
        }
        if (!data.startsWith("STEPS_")) {
            telegramService.sendMessage(session.chatId(), "Неизвестный выбор шагов.");
            return;
        }
        try {
            int steps = Integer.parseInt(data.substring("STEPS_".length()));
            session.steps(steps);
            removeKeyboard(callback.message());
            session.clearLastPromptMessage();
            promptDensity(session);
        } catch (NumberFormatException ex) {
            telegramService.sendMessage(session.chatId(), "Не удалось разобрать количество шагов.");
        }
    }

    private void processDensityChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        if ("DENSITY_MANUAL".equals(data)) {
            session.step(ConversationStep.WAITING_FOR_MANUAL_DENSITY);
            removeKeyboard(callback.message());
            session.clearLastPromptMessage();
            telegramService.sendMessage(session.chatId(), "Введите плотность от 0 до 1 (например 0.05):");
            return;
        }
        if (!data.startsWith("DENSITY_")) {
            telegramService.sendMessage(session.chatId(), "Неизвестный выбор плотности.");
            return;
        }
        try {
            double density = Double.parseDouble(data.substring("DENSITY_".length()));
            session.density(density);
            removeKeyboard(callback.message());
            session.clearLastPromptMessage();
            promptMaskAction(session);
        } catch (NumberFormatException ex) {
            telegramService.sendMessage(session.chatId(), "Не удалось разобрать плотность.");
        }
    }

    private void processMaskChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        removeKeyboard(callback.message());
        session.clearLastPromptMessage();
        if ("MASK_SKIP".equals(data)) {
            session.mask(null);
            promptWrap(session);
        } else if ("MASK_ENTER".equals(data)) {
            session.step(ConversationStep.WAITING_FOR_MASK_INPUT);
            telegramService.sendMessage(session.chatId(), "Введите маску (9 символов 0/1, пробелы игнорируются):");
        } else {
            telegramService.sendMessage(session.chatId(), "Неизвестный выбор маски.");
        }
    }

    private void processWrapChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        removeKeyboard(callback.message());
        session.clearLastPromptMessage();
        switch (data) {
            case "WRAP_ON" -> {
                session.wrap(true);
                promptPalette(session);
            }
            case "WRAP_OFF" -> {
                session.wrap(false);
                promptPalette(session);
            }
            default -> telegramService.sendMessage(session.chatId(), "Неизвестный выбор границ.");
        }
    }

    private void processPaletteChoice(ChatSession session, TelegramCallbackQueryDto callback, String data) {
        if (!data.startsWith("PAL_")) {
            telegramService.sendMessage(session.chatId(), "Неизвестная палитра.");
            return;
        }
        String paletteName = data.substring("PAL_".length());
        try {
            Palette2D palette = Palette2D.valueOf(paletteName);
            session.palette(palette);
        } catch (IllegalArgumentException ex) {
            telegramService.sendMessage(session.chatId(), "Неизвестная палитра: " + paletteName);
            return;
        }
        removeKeyboard(callback.message());
        session.clearLastPromptMessage();
        startGeneration(session);
    }

    private void promptGridSize(ChatSession session) {
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(List.of(
                List.of(
                        button("100×100", "SIZE_100"),
                        button("200×200", "SIZE_200"),
                        button("400×400", "SIZE_400")
                )));
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Выберите размер сетки:", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_SIZE);
    }

    private void promptSteps(ChatSession session) {
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(List.of(
                List.of(button("50", "STEPS_50"), button("100", "STEPS_100"), button("150", "STEPS_150")),
                List.of(button("200", "STEPS_200"), button("300", "STEPS_300")),
                List.of(button("Ручной ввод", "STEPS_MANUAL"))
        ));
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Выберите количество шагов:", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_STEPS);
    }

    private void promptDensity(ChatSession session) {
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(List.of(
                List.of(button("0.01", "DENSITY_0.01"), button("0.05", "DENSITY_0.05"), button("0.1", "DENSITY_0.1")),
                List.of(button("0.2", "DENSITY_0.2")),
                List.of(button("Ручной ввод", "DENSITY_MANUAL"))
        ));
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Выберите плотность начального заполнения:", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_DENSITY);
    }

    private void promptMaskAction(ChatSession session) {
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(List.of(
                List.of(button("Пропустить", "MASK_SKIP"), button("Ввести маску", "MASK_ENTER"))
        ));
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Использовать маску инициализации?", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_MASK_ACTION);
    }

    private void promptWrap(ChatSession session) {
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(List.of(
                List.of(button("Wrap ON", "WRAP_ON"), button("Wrap OFF", "WRAP_OFF"))
        ));
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Границы поля:", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_WRAP);
    }

    private void promptPalette(ChatSession session) {
        List<InlineKeyboardButtonDto> buttons = new ArrayList<>();
        for (Palette2D palette : Palette2D.values()) {
            buttons.add(button(palette.name(), "PAL_" + palette.name()));
        }
        List<List<InlineKeyboardButtonDto>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 3) {
            rows.add(new ArrayList<>(buttons.subList(i, Math.min(i + 3, buttons.size()))));
        }
        InlineKeyboardMarkupDto keyboard = new InlineKeyboardMarkupDto(rows);
        TelegramMessageDto message = telegramService.sendMessage(session.chatId(), "Выберите палитру:", keyboard);
        session.lastPromptMessageId(message.messageId());
        session.step(ConversationStep.CHOOSING_PALETTE);
    }

    private InlineKeyboardButtonDto button(String text, String data) {
        return new InlineKeyboardButtonDto(text, data);
    }

    private void startGeneration(ChatSession session) {
        if (!session.isReadyForGeneration()) {
            telegramService.sendMessage(session.chatId(), "Не хватает данных для генерации. Попробуйте начать заново /start.");
            return;
        }

        TelegramMessageDto loadingMessage = telegramService.sendMessage(session.chatId(), "⏳ Генерируем видео, это может занять несколько секунд...");
        session.loadingMessageId(loadingMessage.messageId());
        session.step(ConversationStep.GENERATING);
        ChatSession snapshot = session.snapshot();

        CompletableFuture.runAsync(() -> generateAndDeliver(snapshot, session), taskExecutor)
                .exceptionally(ex -> {
                    log.error("Generation task failed", ex);
                    return null;
                });
    }

    private void generateAndDeliver(ChatSession snapshot, ChatSession sessionReference) {
        long chatId = snapshot.chatId();
        Integer loadingMessageId = snapshot.loadingMessageId();
        try {
            String ruleLabel = "B" + snapshot.birthDigits() + "/S" + snapshot.survivalDigits();
            Rule rule = Rule.parse(ruleLabel);
            SimulationOptions.Builder builder = SimulationOptions.builder()
                    .rule(rule)
                    .ruleLabel(ruleLabel)
                    .dimensions(new SimulationDimensions(snapshot.width(), snapshot.height(), SimulationDimensions.DEFAULT_SCALE))
                    .steps(snapshot.steps())
                    .delayCs(SimulationOptions.DEFAULT_DELAY_CS)
                    .wrap(Boolean.TRUE.equals(snapshot.wrap()))
                    .density(snapshot.density())
                    .outputFormat(SimulationOutputFormat.MP4)
                    .palette(snapshot.palette())
                    .randomSeed(SeedService.DEFAULT_RANDOM_SEED);
            boolean[] mask = snapshot.mask();
            if (mask != null) {
                builder.initMask(mask);
            }

            SimulationOptions options = builder.build();
            SimulationResult result = simulationService.runSimulation(options);
            telegramService.sendAnimation(chatId, result.fileName(), result.bytes(), result.summary());
        } catch (Exception ex) {
            log.error("Failed to generate simulation for chat {}", chatId, ex);
            telegramService.sendMessage(chatId, "Не удалось сгенерировать видео: " + ex.getMessage());
        } finally {
            if (loadingMessageId != null) {
                try {
                    telegramService.deleteMessage(chatId, loadingMessageId);
                } catch (Exception ex) {
                    log.warn("Failed to delete loading message {} for chat {}", loadingMessageId, chatId, ex);
                }
            }
            sessions.compute(chatId, (id, existing) -> existing == sessionReference ? null : existing);
        }
    }

    private void removeKeyboard(TelegramMessageDto message) {
        if (message == null || message.chat() == null) {
            return;
        }
        try {
            telegramService.editMessageReplyMarkup(message.chat().id(), message.messageId(), null);
        } catch (Exception ex) {
            log.debug("Failed to remove inline keyboard for message {}", message.messageId(), ex);
        }
    }

    private void removeKeyboard(ChatSession session) {
        Integer messageId = session.lastPromptMessageId();
        if (messageId == null) {
            return;
        }
        try {
            telegramService.editMessageReplyMarkup(session.chatId(), messageId, null);
        } catch (Exception ex) {
            log.debug("Failed to remove inline keyboard for chat {} message {}", session.chatId(), messageId, ex);
        }
        session.clearLastPromptMessage();
    }

    private void tryRemoveKeyboard(TelegramMessageDto message) {
        if (message == null || message.chat() == null) {
            return;
        }
        try {
            telegramService.editMessageReplyMarkup(message.chat().id(), message.messageId(), null);
        } catch (Exception ex) {
            log.debug("Failed to remove inline keyboard for dangling callback", ex);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupInactiveSessions() {
        Instant threshold = Instant.now().minus(SESSION_TIMEOUT);
        for (Map.Entry<Long, ChatSession> entry : sessions.entrySet()) {
            ChatSession session = entry.getValue();
            if (session.step() == ConversationStep.GENERATING) {
                continue;
            }
            if (session.lastInteraction().isBefore(threshold)) {
                long chatId = entry.getKey();
                removeKeyboard(session);
                telegramService.sendMessage(chatId, "⏱ Сессия была сброшена из-за бездействия. Отправьте /start, чтобы начать заново.");
                sessions.remove(chatId, session);
            }
        }
    }
}
