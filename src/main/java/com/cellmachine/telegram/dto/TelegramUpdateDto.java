package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUpdateDto(
        @JsonProperty("update_id")
        long updateId,
        TelegramMessageDto message,
        @JsonProperty("callback_query")
        TelegramCallbackQueryDto callbackQuery
) {
}
