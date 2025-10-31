package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramMessageDto(
        @JsonProperty("message_id")
        int messageId,
        TelegramChatDto chat,
        TelegramUserDto from,
        String text
) {
}
