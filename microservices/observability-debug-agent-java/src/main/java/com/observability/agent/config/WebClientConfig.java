package com.observability.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient observabilityWebClient(AgentProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl().replaceAll("/+$", ""))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    @Bean
    public WebClient grafanaWebClient(GrafanaProperties props) {
        return WebClient.builder()
                .baseUrl(props.getApiBaseUrl().replaceAll("/+$", ""))
                .build();
    }
}
