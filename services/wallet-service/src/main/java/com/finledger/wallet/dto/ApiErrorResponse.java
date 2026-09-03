package com.finledger.wallet.dto;

import java.time.Instant;
import java.util.Map;

public class ApiErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final Map<String, String> details;

    public ApiErrorResponse(Instant timestamp, int status, String error, Map<String, String> details) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
