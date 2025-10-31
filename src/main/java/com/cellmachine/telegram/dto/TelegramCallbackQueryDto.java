package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramCallbackQueryDto(
        String id,
        TelegramUserDto from,
        TelegramMessageDto message,
        @JsonProperty("chat_instance")
        String chatInstance,
        String data
) {
}
