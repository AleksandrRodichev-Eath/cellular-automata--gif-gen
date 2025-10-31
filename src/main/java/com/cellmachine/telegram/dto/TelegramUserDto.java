package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUserDto(
        @JsonProperty("id")
        long id,
        @JsonProperty("is_bot")
        Boolean isBot,
        String firstName,
        String lastName,
        String username
) {
}
