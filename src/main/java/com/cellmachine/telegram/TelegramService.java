package com.cellmachine.telegram;

import com.cellmachine.config.AppProperties;
import com.cellmachine.telegram.dto.InlineKeyboardMarkupDto;
import com.cellmachine.telegram.dto.TelegramApiResponse;
import com.cellmachine.telegram.dto.TelegramMessageDto;
import com.cellmachine.telegram.dto.TelegramUpdateDto;
import java.util.HashMap;
import java.util.Locale;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TelegramService {

    private static final Logger log = LoggerFactory.getLogger(TelegramService.class);
    private static final ParameterizedTypeReference<TelegramApiResponse<TelegramMessageDto>> MESSAGE_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TelegramApiResponse<Boolean>> BOOLEAN_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<TelegramApiResponse<TelegramUpdateDto[]>> UPDATES_RESPONSE_TYPE =
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

    public TelegramUpdateDto[] getUpdates(Long offset, int timeoutSeconds) {
        Map<String, Object> payload = new HashMap<>();
        if (offset != null) {
            payload.put("offset", offset);
        }
        payload.put("timeout", timeoutSeconds);
        return postForResult("getUpdates", payload, UPDATES_RESPONSE_TYPE);
    }

    public void sendAnimation(String fileName, byte[] bytes, String caption) {
        sendAnimationInternal(properties.getTelegramChatId(), fileName, bytes, caption);
    }

    public void sendAnimation(long chatId, String fileName, byte[] bytes, String caption) {
        sendAnimationInternal(chatId, fileName, bytes, caption);
    }

    private void sendAnimationInternal(Object chatId, String fileName, byte[] bytes, String caption) {
        long sizeBytes = bytes.length;
        String url = buildApiUrl("sendAnimation");
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        if (caption != null && !caption.isBlank()) {
            body.add("caption", caption);
        }
        body.add("animation", new NamedByteArrayResource(ensureFileName(fileName), bytes));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Telegram API responded with status " + response.getStatusCode());
            }
        } catch (RestClientException ex) {
            if (isPayloadTooLarge(ex)) {
                notifyOversizeAnimation(chatId, sizeBytes);
            }
            throw new IllegalStateException("Failed to send animation to Telegram", ex);
        }
    }

    private boolean isPayloadTooLarge(RestClientException ex) {
        if (ex instanceof HttpClientErrorException clientEx) {
            return clientEx.getStatusCode().value() == HttpStatus.PAYLOAD_TOO_LARGE.value();
        }
        return false;
    }

    private void notifyOversizeAnimation(Object chatId, long sizeBytes) {
        String readable = String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0));
        String message = "Animation is too large to send (" + readable + ").";
        try {
            sendSimpleMessage(chatId, message);
        } catch (Exception notifyEx) {
            log.warn("Failed to notify chat {} about oversize animation", chatId, notifyEx);
        }
    }

    private void sendSimpleMessage(Object chatId, String text) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        postForResult("sendMessage", payload, MESSAGE_RESPONSE_TYPE);
    }

    private String ensureFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "simulation.mp4";
        }
        return fileName;
    }

    private String buildApiUrl(String method) {
        String baseUrl = properties.getTelegramBaseUrl().orElse("https://api.telegram.org").replaceAll("/+$$", "");
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
