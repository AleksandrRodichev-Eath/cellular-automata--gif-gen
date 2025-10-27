package com.cellmachine.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AppProperties {

    private final String telegramBotToken;
    private final String telegramChatId;
    private final String telegramBaseUrl;
    private final String bindHost;
    private final int bindPort;

    public AppProperties(Environment environment) {
        this.telegramBotToken = resolveRequired(environment, "app.telegram-bot-token", "TELEGRAM_BOT_TOKEN");
        this.telegramChatId = resolveRequired(environment, "app.telegram-chat-id", "TELEGRAM_CHAT_ID");
        this.telegramBaseUrl = resolveOptional(environment, "app.telegram-base-url", "TELEGRAM_BASE_URL");

        BindAddress address = determineBindAddress(environment);
        this.bindHost = address.host();
        this.bindPort = address.port();
    }

    public String getTelegramBotToken() {
        return telegramBotToken;
    }

    public String getTelegramChatId() {
        return telegramChatId;
    }

    public Optional<String> getTelegramBaseUrl() {
        return Optional.ofNullable(telegramBaseUrl);
    }

    public String getBindHost() {
        return bindHost;
    }

    public int getBindPort() {
        return bindPort;
    }

    public InetAddress getBindAddress() {
        try {
            return InetAddress.getByName(bindHost);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("Failed to resolve bind host: " + bindHost, ex);
        }
    }

    private String resolveRequired(Environment environment, String propertyKey, String envKey) {
        String value = resolveOptional(environment, propertyKey, envKey);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required configuration: " + envKey);
        }
        return value;
    }

    private String resolveOptional(Environment environment, String propertyKey, String envKey) {
        String value = environment.getProperty(propertyKey);
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        value = environment.getProperty(envKey);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private BindAddress determineBindAddress(Environment environment) {
        String bindRaw = resolveOptional(environment, "app.bind-address", "APP_BIND_ADDR");
        String defaultHost = "0.0.0.0";
        int defaultPort = 3000;

        if (StringUtils.hasText(bindRaw)) {
            String[] parts = bindRaw.split(":", 2);
            if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
                throw new IllegalStateException("APP_BIND_ADDR must follow host:port format");
            }
            int port = parsePort(parts[1]);
            return new BindAddress(parts[0].trim(), port);
        }

        String portValue = resolveOptional(environment, "server.port", "PORT");
        if (StringUtils.hasText(portValue)) {
            int port = parsePort(portValue);
            return new BindAddress(defaultHost, port);
        }

        return new BindAddress(defaultHost, defaultPort);
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value.trim());
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException();
            }
            return port;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid port value: " + value, ex);
        }
    }

    private record BindAddress(String host, int port) {}
}
