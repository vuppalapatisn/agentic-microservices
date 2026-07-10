package com.observability.agent.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InvestigationRequest {

    @NotBlank
    @Size(min = 3, message = "Query must be at least 3 characters")
    private String query;

    @JsonAlias("correlationId")
    private String correlationId;

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
