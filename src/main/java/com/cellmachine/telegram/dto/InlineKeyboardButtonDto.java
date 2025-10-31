package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InlineKeyboardButtonDto(
        String text,
        @JsonProperty("callback_data")
        String callbackData
) {
}
