package com.cellmachine.telegram;

import com.cellmachine.telegram.bot.TelegramBotService;
import com.cellmachine.telegram.dto.TelegramUpdateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private final TelegramBotService botService;

    public TelegramWebhookController(TelegramBotService botService) {
        this.botService = botService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleUpdate(@RequestBody TelegramUpdateDto update) {
        botService.handleUpdate(update);
        return ResponseEntity.ok().build();
    }
}
