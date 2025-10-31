package com.cellmachine.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InlineKeyboardMarkupDto(
        @JsonProperty("inline_keyboard")
        List<List<InlineKeyboardButtonDto>> inlineKeyboard
) {
}
