package com.amol.microservices.observability;

import com.amol.microservices.observability.config.ObservabilityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ObservabilityServerApplication.class, args);
    }
}
