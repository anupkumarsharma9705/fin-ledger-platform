package com.finledger.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.wallet.dto.LedgerEntryResponse;
import com.finledger.wallet.dto.LedgerLineRequest;
import com.finledger.wallet.dto.LedgerLineResponse;
import com.finledger.wallet.dto.PostLedgerEntryRequest;
import com.finledger.wallet.exception.ApiException;
import com.finledger.wallet.model.CurrencyCode;
import com.finledger.wallet.model.EntryStatus;
import com.finledger.wallet.model.EntryType;
import com.finledger.wallet.model.LedgerEntry;
import com.finledger.wallet.model.LedgerLine;
import com.finledger.wallet.model.PostingDirection;
import com.finledger.wallet.model.Wallet;
import com.finledger.wallet.repository.LedgerEntryRepository;
import com.finledger.wallet.repository.LedgerLineRepository;
import com.finledger.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerService {

    private static final int MONEY_SCALE = 2;

    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerLineRepository ledgerLineRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;

    public LedgerService(
            LedgerEntryRepository ledgerEntryRepository,
            LedgerLineRepository ledgerLineRepository,
            WalletRepository walletRepository,
            ObjectMapper objectMapper
    ) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerLineRepository = ledgerLineRepository;
        this.walletRepository = walletRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LedgerEntryResponse postEntry(String idempotencyKey, PostLedgerEntryRequest request, LedgerRequestMetadata metadata) {
        String normalizedIdempotencyKey = normalizeRequired(idempotencyKey, "Idempotency-Key header is required");
        validateEntryRequest(request);
        String requestHash = calculateRequestHash(request, null);

        return ledgerEntryRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> resolveIdempotency(existing, requestHash))
                .orElseGet(() -> persistNewEntry(normalizedIdempotencyKey, requestHash, request, metadata, null));
    }

    @Transactional(readOnly = true)
    public LedgerEntryResponse getEntry(UUID entryId) {
        LedgerEntry entry = ledgerEntryRepository.findById(entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found"));
        List<LedgerLine> lines = ledgerLineRepository.findAllByEntryIdOrderById(entryId);
        return toResponse(entry, lines);
    }

    @Transactional
    public LedgerEntryResponse postPending(UUID entryId) {
        LedgerEntry entry = ledgerEntryRepository.findByIdForUpdate(entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found"));
        if (entry.getStatus() != EntryStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending entries can be posted");
        }
        List<LedgerLine> lines = ledgerLineRepository.findAllByEntryIdOrderById(entryId);
        applyWalletBalanceDeltas(lines, entry.getCurrency(), true);
        entry.setStatus(EntryStatus.POSTED);
        entry.setPostedAt(Instant.now());
        LedgerEntry saved = ledgerEntryRepository.save(entry);
        return toResponse(saved, lines);
    }

    @Transactional
    public LedgerEntryResponse voidPending(UUID entryId) {
        LedgerEntry entry = ledgerEntryRepository.findByIdForUpdate(entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found"));
        if (entry.getStatus() != EntryStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Only pending entries can be voided");
        }
        entry.setStatus(EntryStatus.VOIDED);
        entry.setVoidedAt(Instant.now());
        LedgerEntry saved = ledgerEntryRepository.save(entry);
        List<LedgerLine> lines = ledgerLineRepository.findAllByEntryIdOrderById(entryId);
        return toResponse(saved, lines);
    }

    @Transactional
    public LedgerEntryResponse reverseEntry(
            UUID entryId,
            EntryType reverseType,
            String idempotencyKey,
            String description,
            LedgerRequestMetadata metadata
    ) {
        String normalizedIdempotencyKey = normalizeRequired(idempotencyKey, "Idempotency-Key header is required");
        String normalizedDescription = normalizeRequired(description, "description is required");
        if (reverseType != EntryType.REVERSAL && reverseType != EntryType.REFUND) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported reversal type");
        }

        LedgerEntry targetEntry = ledgerEntryRepository.findByIdForUpdate(entryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found"));
        if (targetEntry.getStatus() != EntryStatus.POSTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only posted entries can be reversed or refunded");
        }
        List<LedgerLine> targetLines = ledgerLineRepository.findAllByEntryIdOrderById(entryId);

        String requestHash = calculateRequestHash(null, reverseType.name() + ":" + entryId + ":" + normalizedDescription);
        return ledgerEntryRepository.findByIdempotencyKey(normalizedIdempotencyKey)
                .map(existing -> resolveIdempotency(existing, requestHash))
                .orElseGet(() -> {
                    PostLedgerEntryRequest reverseRequest = new PostLedgerEntryRequest();
                    reverseRequest.setEntryType(reverseType);
                    reverseRequest.setStatus(EntryStatus.POSTED);
                    reverseRequest.setCurrency(targetEntry.getCurrency());
                    reverseRequest.setDescription(normalizedDescription);
                    reverseRequest.setExternalReference(null);
                    reverseRequest.setLines(targetLines.stream()
                            .map(this::reverseLineRequest)
                            .toList());
                    return persistNewEntry(
                            normalizedIdempotencyKey,
                            requestHash,
                            reverseRequest,
                            metadata,
                            targetEntry.getId()
                    );
                });
    }

    private LedgerLineRequest reverseLineRequest(LedgerLine line) {
        LedgerLineRequest request = new LedgerLineRequest();
        request.setWalletId(line.getWalletId());
        request.setAmount(line.getAmount());
        request.setDirection(line.getDirection() == PostingDirection.DEBIT ? PostingDirection.CREDIT : PostingDirection.DEBIT);
        return request;
    }

    private LedgerEntryResponse resolveIdempotency(LedgerEntry existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "Idempotency key already used with a different request");
        }
        List<LedgerLine> lines = ledgerLineRepository.findAllByEntryIdOrderById(existing.getId());
        return toResponse(existing, lines);
    }

    private LedgerEntryResponse persistNewEntry(
            String idempotencyKey,
            String requestHash,
            PostLedgerEntryRequest request,
            LedgerRequestMetadata metadata,
            UUID reversalOfEntryId
    ) {
        validateLines(request.getCurrency(), request.getLines());
        List<Wallet> wallets = lockWallets(request.getLines());
        ensureWalletCurrencies(request.getCurrency(), request.getLines(), wallets);
        if (request.getStatus() == EntryStatus.POSTED) {
            applyWalletBalanceDeltas(request.getLines(), request.getCurrency(), wallets, true);
        }

        LedgerEntry entry = new LedgerEntry();
        entry.setId(UUID.randomUUID());
        entry.setIdempotencyKey(idempotencyKey);
        entry.setRequestHash(requestHash);
        entry.setExternalReference(normalizeOptional(request.getExternalReference()));
        entry.setEntryType(request.getEntryType());
        entry.setStatus(request.getStatus());
        entry.setCurrency(request.getCurrency());
        entry.setDescription(request.getDescription().trim());
        entry.setReversalOfEntryId(reversalOfEntryId);
        entry.setCorrelationId(metadata.getCorrelationId());
        entry.setRequestId(metadata.getRequestId());
        entry.setActor(metadata.getActor());
        entry.setSource(metadata.getSource());
        Instant now = Instant.now();
        entry.setCreatedAt(now);
        if (request.getStatus() == EntryStatus.POSTED) {
            entry.setPostedAt(now);
        } else if (request.getStatus() == EntryStatus.VOIDED) {
            entry.setVoidedAt(now);
        }

        try {
            LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
            List<LedgerLine> lines = request.getLines().stream().map(line -> toLedgerLine(savedEntry.getId(), request.getCurrency(), line)).toList();
            ledgerLineRepository.saveAll(lines);
            return toResponse(savedEntry, lines);
        } catch (DataIntegrityViolationException ex) {
            return ledgerEntryRepository.findByIdempotencyKey(idempotencyKey)
                    .map(existing -> resolveIdempotency(existing, requestHash))
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Duplicate external reference or duplicate reversal detected"));
        }
    }

    private LedgerLine toLedgerLine(UUID entryId, CurrencyCode currency, LedgerLineRequest line) {
        LedgerLine ledgerLine = new LedgerLine();
        ledgerLine.setId(UUID.randomUUID());
        ledgerLine.setEntryId(entryId);
        ledgerLine.setWalletId(line.getWalletId());
        ledgerLine.setDirection(line.getDirection());
        ledgerLine.setAmount(normalizeMoney(line.getAmount()));
        ledgerLine.setCurrency(currency);
        ledgerLine.setCreatedAt(Instant.now());
        return ledgerLine;
    }

    private List<Wallet> lockWallets(List<LedgerLineRequest> lines) {
        List<UUID> walletIds = lines.stream()
                .map(LedgerLineRequest::getWalletId)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Wallet> wallets = walletRepository.findAllByIdInForUpdate(walletIds);
        if (wallets.size() != walletIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more wallet accounts were not found");
        }
        return wallets;
    }

    private void ensureWalletCurrencies(CurrencyCode currency, List<LedgerLineRequest> lines, List<Wallet> wallets) {
        Map<UUID, Wallet> walletMap = wallets.stream().collect(Collectors.toMap(Wallet::getId, Function.identity()));
        for (LedgerLineRequest line : lines) {
            Wallet wallet = walletMap.get(line.getWalletId());
            if (wallet == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "One or more wallet accounts were not found");
            }
            if (wallet.getCurrency() != currency) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Cross-currency posting is not allowed");
            }
        }
    }

    private void applyWalletBalanceDeltas(List<LedgerLine> lines, CurrencyCode currency, boolean persistWallets) {
        List<LedgerLineRequest> lineRequests = lines.stream().map(line -> {
            LedgerLineRequest request = new LedgerLineRequest();
            request.setWalletId(line.getWalletId());
            request.setDirection(line.getDirection());
            request.setAmount(line.getAmount());
            return request;
        }).toList();
        List<Wallet> wallets = lockWallets(lineRequests);
        ensureWalletCurrencies(currency, lineRequests, wallets);
        applyWalletBalanceDeltas(lineRequests, currency, wallets, persistWallets);
    }

    private void applyWalletBalanceDeltas(
            List<LedgerLineRequest> lines,
            CurrencyCode currency,
            List<Wallet> lockedWallets,
            boolean persistWallets
    ) {
        Map<UUID, Wallet> walletMap = lockedWallets.stream().collect(Collectors.toMap(Wallet::getId, Function.identity()));
        Map<UUID, BigDecimal> deltas = new LinkedHashMap<>();
        for (LedgerLineRequest line : lines) {
            BigDecimal amount = normalizeMoney(line.getAmount());
            BigDecimal signedDelta = line.getDirection() == PostingDirection.CREDIT ? amount : amount.negate();
            deltas.merge(line.getWalletId(), signedDelta, BigDecimal::add);
        }

        Instant now = Instant.now();
        for (Map.Entry<UUID, BigDecimal> deltaEntry : deltas.entrySet()) {
            Wallet wallet = walletMap.get(deltaEntry.getKey());
            BigDecimal nextBalance = normalizeMoney(wallet.getBalance().add(deltaEntry.getValue()));
            if (nextBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Posting would make wallet balance negative");
            }
            wallet.setBalance(nextBalance);
            wallet.setUpdatedAt(now);
        }
        if (persistWallets) {
            walletRepository.saveAll(lockedWallets);
        }
    }

    private void validateEntryRequest(PostLedgerEntryRequest request) {
        if (request.getEntryType() == EntryType.REVERSAL || request.getEntryType() == EntryType.REFUND) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use reversal or refund endpoints for compensating entries");
        }
        if (request.getStatus() == EntryStatus.VOIDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New entries cannot start in VOIDED status");
        }
    }

    private void validateLines(CurrencyCode currency, List<LedgerLineRequest> lines) {
        if (lines.size() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A ledger entry requires at least two lines");
        }
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        boolean hasDebit = false;
        boolean hasCredit = false;

        for (LedgerLineRequest line : lines) {
            BigDecimal amount = normalizeMoney(line.getAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ledger line amount must be positive");
            }
            if (line.getDirection() == PostingDirection.DEBIT) {
                hasDebit = true;
                debitTotal = debitTotal.add(amount);
            } else if (line.getDirection() == PostingDirection.CREDIT) {
                hasCredit = true;
                creditTotal = creditTotal.add(amount);
            } else {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ledger line direction is required");
            }
        }

        if (!hasDebit || !hasCredit) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A ledger entry must include both debit and credit lines");
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ledger entry is unbalanced");
        }
        if (currency == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "currency is required");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        try {
            return value.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amount scale must be 2 decimal places");
        }
    }

    private String calculateRequestHash(PostLedgerEntryRequest request, String supplementalInput) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = request == null ? supplementalInput : objectMapper.writeValueAsString(request);
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to hash request");
        }
    }

    private LedgerEntryResponse toResponse(LedgerEntry entry, List<LedgerLine> lines) {
        BigDecimal totalDebit = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal totalCredit = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
        List<LedgerLineResponse> lineResponses = new ArrayList<>();
        for (LedgerLine line : lines) {
            if (line.getDirection() == PostingDirection.DEBIT) {
                totalDebit = totalDebit.add(line.getAmount());
            } else {
                totalCredit = totalCredit.add(line.getAmount());
            }
            lineResponses.add(new LedgerLineResponse(
                    line.getWalletId(),
                    line.getDirection(),
                    line.getAmount(),
                    line.getCurrency().name()
            ));
        }

        return new LedgerEntryResponse(
                entry.getId(),
                entry.getIdempotencyKey(),
                entry.getExternalReference(),
                entry.getEntryType(),
                entry.getStatus(),
                entry.getCurrency().name(),
                totalDebit,
                totalCredit,
                entry.getReversalOfEntryId(),
                entry.getCorrelationId(),
                entry.getRequestId(),
                entry.getActor(),
                entry.getSource(),
                entry.getCreatedAt(),
                entry.getPostedAt(),
                entry.getVoidedAt(),
                lineResponses
        );
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
