package com.finledger.wallet.dto;

import com.finledger.wallet.model.EntryStatus;
import com.finledger.wallet.model.EntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class LedgerEntryResponse {

    private final UUID entryId;
    private final String idempotencyKey;
    private final String externalReference;
    private final EntryType entryType;
    private final EntryStatus status;
    private final String currency;
    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;
    private final UUID reversalOfEntryId;
    private final String correlationId;
    private final String requestId;
    private final String actor;
    private final String source;
    private final Instant createdAt;
    private final Instant postedAt;
    private final Instant voidedAt;
    private final List<LedgerLineResponse> lines;

    public LedgerEntryResponse(
            UUID entryId,
            String idempotencyKey,
            String externalReference,
            EntryType entryType,
            EntryStatus status,
            String currency,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            UUID reversalOfEntryId,
            String correlationId,
            String requestId,
            String actor,
            String source,
            Instant createdAt,
            Instant postedAt,
            Instant voidedAt,
            List<LedgerLineResponse> lines
    ) {
        this.entryId = entryId;
        this.idempotencyKey = idempotencyKey;
        this.externalReference = externalReference;
        this.entryType = entryType;
        this.status = status;
        this.currency = currency;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.reversalOfEntryId = reversalOfEntryId;
        this.correlationId = correlationId;
        this.requestId = requestId;
        this.actor = actor;
        this.source = source;
        this.createdAt = createdAt;
        this.postedAt = postedAt;
        this.voidedAt = voidedAt;
        this.lines = lines;
    }

    public UUID getEntryId() {
        return entryId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public UUID getReversalOfEntryId() {
        return reversalOfEntryId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getActor() {
        return actor;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public Instant getVoidedAt() {
        return voidedAt;
    }

    public List<LedgerLineResponse> getLines() {
        return lines;
    }
}
