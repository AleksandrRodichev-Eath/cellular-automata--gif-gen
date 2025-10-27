package com.cellmachine.config;

import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

@Component
public class BindAddressCustomizer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

    private final AppProperties properties;

    public BindAddressCustomizer(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(ConfigurableServletWebServerFactory factory) {
        factory.setAddress(properties.getBindAddress());
        factory.setPort(properties.getBindPort());
    }
}
