package com.finledger.wallet.controller;

import com.finledger.wallet.dto.LedgerEntryResponse;
import com.finledger.wallet.dto.PostLedgerEntryRequest;
import com.finledger.wallet.model.EntryType;
import com.finledger.wallet.service.LedgerRequestMetadata;
import com.finledger.wallet.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger/entries")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryResponse postEntry(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Source", required = false) String source,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @Valid @RequestBody PostLedgerEntryRequest request,
            Principal principal
    ) {
        return ledgerService.postEntry(idempotencyKey, request, metadata(requestId, correlationId, source, actor, principal));
    }

    @GetMapping("/{entryId}")
    public LedgerEntryResponse getEntry(@PathVariable UUID entryId) {
        return ledgerService.getEntry(entryId);
    }

    @PostMapping("/{entryId}/post")
    public LedgerEntryResponse postPending(@PathVariable UUID entryId) {
        return ledgerService.postPending(entryId);
    }

    @PostMapping("/{entryId}/void")
    public LedgerEntryResponse voidPending(@PathVariable UUID entryId) {
        return ledgerService.voidPending(entryId);
    }

    @PostMapping("/{entryId}/reverse")
    public LedgerEntryResponse reverse(
            @PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Source", required = false) String source,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @RequestHeader(value = "X-Description", required = false) String description,
            Principal principal
    ) {
        return ledgerService.reverseEntry(
                entryId,
                EntryType.REVERSAL,
                idempotencyKey,
                description == null ? "Reversal" : description,
                metadata(requestId, correlationId, source, actor, principal)
        );
    }

    @PostMapping("/{entryId}/refund")
    public LedgerEntryResponse refund(
            @PathVariable UUID entryId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(value = "X-Source", required = false) String source,
            @RequestHeader(value = "X-Actor", required = false) String actor,
            @RequestHeader(value = "X-Description", required = false) String description,
            Principal principal
    ) {
        return ledgerService.reverseEntry(
                entryId,
                EntryType.REFUND,
                idempotencyKey,
                description == null ? "Refund" : description,
                metadata(requestId, correlationId, source, actor, principal)
        );
    }

    private LedgerRequestMetadata metadata(String requestId, String correlationId, String source, String actor, Principal principal) {
        String principalName = principal instanceof Authentication authentication ? authentication.getName() : "system";
        return new LedgerRequestMetadata(
                defaultHeader(requestId),
                defaultHeader(correlationId),
                actor == null || actor.isBlank() ? principalName : actor.trim(),
                source == null || source.isBlank() ? "wallet-api" : source.trim()
        );
    }

    private String defaultHeader(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
