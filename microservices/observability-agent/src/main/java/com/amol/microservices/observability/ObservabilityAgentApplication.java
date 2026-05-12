package com.amol.microservices.observability;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObservabilityAgentApplication.class, args);
    }
}
