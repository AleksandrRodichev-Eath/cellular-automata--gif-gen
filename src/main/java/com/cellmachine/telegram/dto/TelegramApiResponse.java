package com.cellmachine.telegram.dto;

public record TelegramApiResponse<T>(
        boolean ok,
        T result
) {
}
