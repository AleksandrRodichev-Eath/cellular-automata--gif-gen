package com.cellmachine.telegram;

import com.cellmachine.config.AppProperties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final AppProperties properties;

    public TelegramService(AppProperties properties) {
        this.properties = properties;
    }

    public void sendAnimation(String fileName, byte[] bytes, String caption) {
        String url = buildSendAnimationUrl();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", properties.getTelegramChatId());
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

    private String buildSendAnimationUrl() {
        String baseUrl = properties.getTelegramBaseUrl().orElse("https://api.telegram.org").replaceAll("/+$", "");
        if (!baseUrl.endsWith("/bot")) {
            baseUrl = baseUrl + "/bot";
        }
        return baseUrl + properties.getTelegramBotToken() + "/sendAnimation";
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
