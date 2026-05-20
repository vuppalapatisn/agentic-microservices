package com.amol.microservices.observability.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LokiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseLokiTimestamp_handlesStringNanoseconds() throws Exception {
        var node = objectMapper.readTree("\"1716214529812000000\"");
        Instant instant = LokiClient.parseLokiTimestamp(node);
        assertEquals(1716214529L, instant.getEpochSecond());
    }

    @Test
    void parseLokiTimestamp_handlesNumericNanoseconds() throws Exception {
        var node = objectMapper.readTree("1716214529812000000");
        Instant instant = LokiClient.parseLokiTimestamp(node);
        assertEquals(1716214529L, instant.getEpochSecond());
    }

    @Test
    void parseLokiTimestamp_handlesScientificNotationFromJackson() throws Exception {
        var node = objectMapper.readTree("[1.716214529812E18, \"log line\"]").get(0);
        Instant instant = LokiClient.parseLokiTimestamp(node);
        assertEquals(1716214529L, instant.getEpochSecond());
    }

    @Test
    void parseLogs_parsesTypicalLokiStreamsResponse() throws Exception {
        String response = """
                {
                  "status": "success",
                  "data": {
                    "resultType": "streams",
                    "result": [
                      {
                        "stream": { "app": "ecommerce", "namespace": "ecommerce" },
                        "values": [
                          [1716214529812000000, "{\\"service\\":\\"ecommerce\\",\\"level\\":\\"INFO\\",\\"message\\":\\"ok\\"}"],
                          ["1716214529813000000", "plain text log line"]
                        ]
                      }
                    ]
                  }
                }
                """;

        var client = new LokiClient(
                new com.amol.microservices.observability.config.ObservabilityProperties(),
                objectMapper);

        var method = LokiClient.class.getDeclaredMethod("parseLogs", String.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        var logs = (java.util.List<com.amol.microservices.observability.dto.LogEntryDto>) method.invoke(client, response);

        assertEquals(2, logs.size());
        assertEquals("ecommerce", logs.get(0).service());
        assertEquals("INFO", logs.get(0).level());
        assertEquals("ok", logs.get(0).message());
    }
}
