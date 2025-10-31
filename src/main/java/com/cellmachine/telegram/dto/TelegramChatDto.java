package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramChatDto(
        @JsonProperty("id")
        long id
) {
}
