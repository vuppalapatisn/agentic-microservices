package com.amol.microservices.observability.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI observabilityOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Observability Server API")
                        .description("Query logs from Loki and metrics from Prometheus for ecommerce microservices.")
                        .version("0.0.1"));
    }
}
