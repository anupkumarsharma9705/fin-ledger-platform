package com.finledger.wallet.service;

public class LedgerRequestMetadata {

    private final String requestId;
    private final String correlationId;
    private final String actor;
    private final String source;

    public LedgerRequestMetadata(String requestId, String correlationId, String actor, String source) {
        this.requestId = requestId;
        this.correlationId = correlationId;
        this.actor = actor;
        this.source = source;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getActor() {
        return actor;
    }

    public String getSource() {
        return source;
    }
}
