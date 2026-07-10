package com.observability.agent.model;

import java.util.List;

public class CorrelationFinding {

    private String probableRootCause;
    private List<String> evidence;

    public CorrelationFinding(String probableRootCause, List<String> evidence) {
        this.probableRootCause = probableRootCause;
        this.evidence = evidence;
    }

    public String getProbableRootCause() { return probableRootCause; }
    public List<String> getEvidence() { return evidence; }
}
