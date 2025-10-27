package com.cellmachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class CellMachineGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CellMachineGeneratorApplication.class, args);
    }
}
