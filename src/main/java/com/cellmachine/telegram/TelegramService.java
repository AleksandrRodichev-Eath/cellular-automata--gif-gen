package com.cellmachine.telegram;

import com.cellmachine.config.AppProperties;
import com.cellmachine.telegram.dto.InlineKeyboardMarkupDto;
import com.cellmachine.telegram.dto.TelegramApiResponse;
import com.cellmachine.telegram.dto.TelegramMessageDto;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

    private static final ParameterizedTypeReference<TelegramApiResponse<TelegramMessageDto>> MESSAGE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TelegramApiResponse<Boolean>> BOOLEAN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestTemplate restTemplate = new RestTemplate();
    private final AppProperties properties;

    public TelegramService(AppProperties properties) {
        this.properties = properties;
    }

    public TelegramMessageDto sendMessage(long chatId, String text) {
        return sendMessage(chatId, text, null);
    }

    public TelegramMessageDto sendMessage(long chatId, String text, InlineKeyboardMarkupDto keyboard) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        if (keyboard != null) {
            payload.put("reply_markup", keyboard);
        }
        return postForResult("sendMessage", payload, MESSAGE_RESPONSE_TYPE);
    }

    public void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkupDto keyboard) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        payload.put("reply_markup", keyboard);
        postForResult("editMessageReplyMarkup", payload, MESSAGE_RESPONSE_TYPE);
    }

    public void deleteMessage(long chatId, int messageId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("message_id", messageId);
        postForResult("deleteMessage", payload, BOOLEAN_RESPONSE_TYPE);
    }

    public void answerCallback(String callbackId, String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("callback_query_id", callbackId);
        if (text != null && !text.isBlank()) {
            payload.put("text", text);
        }
        postForResult("answerCallbackQuery", payload, BOOLEAN_RESPONSE_TYPE);
    }

    public void sendAnimation(String fileName, byte[] bytes, String caption) {
        sendAnimationInternal(properties.getTelegramChatId(), fileName, bytes, caption);
    }

    public void sendAnimation(long chatId, String fileName, byte[] bytes, String caption) {
        sendAnimationInternal(chatId, fileName, bytes, caption);
    }

    private void sendAnimationInternal(Object chatId, String fileName, byte[] bytes, String caption) {
        String url = buildApiUrl("sendAnimation");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        if (caption != null && !caption.isBlank()) {
            body.add("caption", caption);
        }
        body.add("animation", new NamedByteArrayResource(fileName, bytes));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Telegram API responded with status " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to send animation to Telegram", ex);
        }
    }

    public String buildWebhookUrl() {
        return buildApiUrl("setWebhook");
    }

    private String buildApiUrl(String method) {
        String baseUrl = properties.getTelegramBaseUrl().orElse("https://api.telegram.org").replaceAll("/+$", "");
        if (!baseUrl.endsWith("/bot")) {
            baseUrl = baseUrl + "/bot";
        }
        return baseUrl + properties.getTelegramBotToken() + "/" + method;
    }

    private <T> T postForResult(String method, Map<String, Object> payload, ParameterizedTypeReference<TelegramApiResponse<T>> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        try {
            ResponseEntity<TelegramApiResponse<T>> response = restTemplate.exchange(
                    buildApiUrl(method),
                    HttpMethod.POST,
                    request,
                    type);
            TelegramApiResponse<T> body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Telegram API returned empty response for method " + method);
            }
            if (!body.ok()) {
                throw new IllegalStateException("Telegram API method " + method + " responded with ok=false");
            }
            return body.result();
        } catch (RestClientException ex) {
            throw new IllegalStateException("Failed to call Telegram API method " + method, ex);
        }
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String fileName;

        private NamedByteArrayResource(String fileName, byte[] byteArray) {
            super(byteArray);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
