package com.finledger.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finledger.wallet.dto.LedgerEntryResponse;
import com.finledger.wallet.dto.LedgerLineRequest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private LedgerLineRepository ledgerLineRepository;
    @Mock
    private WalletRepository walletRepository;

    private LedgerService ledgerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerEntryRepository, ledgerLineRepository, walletRepository, objectMapper);
    }

    @Test
    void postEntryShouldPersistBalancedPostedEntry() {
        Wallet debitWallet = wallet(UUID.randomUUID(), CurrencyCode.USD, "100.00");
        Wallet creditWallet = wallet(UUID.randomUUID(), CurrencyCode.USD, "5.00");
        PostLedgerEntryRequest request = baseRequest(EntryType.POSTING, EntryStatus.POSTED, List.of(
                line(debitWallet.getId(), PostingDirection.DEBIT, "10.00"),
                line(creditWallet.getId(), PostingDirection.CREDIT, "10.00")
        ));

        when(ledgerEntryRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(walletRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(creditWallet, debitWallet));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerEntryResponse response = ledgerService.postEntry("idem-1", request, metadata());

        assertNotNull(response.getEntryId());
        assertEquals(EntryStatus.POSTED, response.getStatus());
        assertEquals(new BigDecimal("10.00"), response.getTotalDebit());
        assertEquals(new BigDecimal("10.00"), response.getTotalCredit());
        assertEquals(new BigDecimal("90.00"), debitWallet.getBalance());
        assertEquals(new BigDecimal("15.00"), creditWallet.getBalance());
        verify(walletRepository).saveAll(any());
    }

    @Test
    void postEntryShouldRejectUnbalancedLines() {
        PostLedgerEntryRequest request = baseRequest(EntryType.POSTING, EntryStatus.POSTED, List.of(
                line(UUID.randomUUID(), PostingDirection.DEBIT, "11.00"),
                line(UUID.randomUUID(), PostingDirection.CREDIT, "10.00")
        ));

        ApiException exception = assertThrows(ApiException.class,
                () -> ledgerService.postEntry("idem-2", request, metadata()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Ledger entry is unbalanced", exception.getMessage());
        verify(walletRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void postEntryShouldRejectCrossCurrencyWallet() {
        Wallet debitWallet = wallet(UUID.randomUUID(), CurrencyCode.USD, "100.00");
        Wallet creditWallet = wallet(UUID.randomUUID(), CurrencyCode.EUR, "0.00");
        PostLedgerEntryRequest request = baseRequest(EntryType.POSTING, EntryStatus.POSTED, List.of(
                line(debitWallet.getId(), PostingDirection.DEBIT, "10.00"),
                line(creditWallet.getId(), PostingDirection.CREDIT, "10.00")
        ));

        when(ledgerEntryRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.empty());
        when(walletRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(creditWallet, debitWallet));

        ApiException exception = assertThrows(ApiException.class,
                () -> ledgerService.postEntry("idem-3", request, metadata()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Cross-currency posting is not allowed", exception.getMessage());
    }

    @Test
    void postEntryShouldReplayWhenIdempotencyMatches() throws JsonProcessingException, NoSuchAlgorithmException {
        PostLedgerEntryRequest request = baseRequest(EntryType.POSTING, EntryStatus.PENDING, List.of(
                line(UUID.randomUUID(), PostingDirection.DEBIT, "4.00"),
                line(UUID.randomUUID(), PostingDirection.CREDIT, "4.00")
        ));
        String hash = hash(objectMapper.writeValueAsString(request));

        LedgerEntry existingEntry = new LedgerEntry();
        existingEntry.setId(UUID.randomUUID());
        existingEntry.setIdempotencyKey("idem-4");
        existingEntry.setRequestHash(hash);
        existingEntry.setEntryType(EntryType.POSTING);
        existingEntry.setStatus(EntryStatus.PENDING);
        existingEntry.setCurrency(CurrencyCode.USD);
        existingEntry.setDescription("existing");
        existingEntry.setCorrelationId("corr");
        existingEntry.setRequestId("req");
        existingEntry.setActor("actor");
        existingEntry.setSource("source");
        existingEntry.setCreatedAt(Instant.now());

        LedgerLine line = new LedgerLine();
        line.setEntryId(existingEntry.getId());
        line.setWalletId(UUID.randomUUID());
        line.setDirection(PostingDirection.DEBIT);
        line.setAmount(new BigDecimal("4.00"));
        line.setCurrency(CurrencyCode.USD);
        line.setCreatedAt(Instant.now());

        when(ledgerEntryRepository.findByIdempotencyKey("idem-4")).thenReturn(Optional.of(existingEntry));
        when(ledgerLineRepository.findAllByEntryIdOrderById(existingEntry.getId())).thenReturn(List.of(line));

        LedgerEntryResponse response = ledgerService.postEntry("idem-4", request, metadata());

        assertEquals(existingEntry.getId(), response.getEntryId());
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void postEntryShouldFailWhenIdempotencyConflicts() {
        PostLedgerEntryRequest request = baseRequest(EntryType.POSTING, EntryStatus.PENDING, List.of(
                line(UUID.randomUUID(), PostingDirection.DEBIT, "4.00"),
                line(UUID.randomUUID(), PostingDirection.CREDIT, "4.00")
        ));

        LedgerEntry existingEntry = new LedgerEntry();
        existingEntry.setId(UUID.randomUUID());
        existingEntry.setIdempotencyKey("idem-5");
        existingEntry.setRequestHash("another-hash");

        when(ledgerEntryRepository.findByIdempotencyKey("idem-5")).thenReturn(Optional.of(existingEntry));

        ApiException exception = assertThrows(ApiException.class,
                () -> ledgerService.postEntry("idem-5", request, metadata()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("Idempotency key already used with a different request", exception.getMessage());
    }

    @Test
    void reverseEntryShouldCreateCompensatingPostedEntry() {
        UUID originalEntryId = UUID.randomUUID();
        Wallet walletA = wallet(UUID.randomUUID(), CurrencyCode.USD, "20.00");
        Wallet walletB = wallet(UUID.randomUUID(), CurrencyCode.USD, "10.00");

        LedgerEntry original = new LedgerEntry();
        original.setId(originalEntryId);
        original.setStatus(EntryStatus.POSTED);
        original.setCurrency(CurrencyCode.USD);
        original.setEntryType(EntryType.POSTING);
        original.setDescription("original");

        LedgerLine debitLine = persistedLine(originalEntryId, walletA.getId(), PostingDirection.DEBIT, "5.00");
        LedgerLine creditLine = persistedLine(originalEntryId, walletB.getId(), PostingDirection.CREDIT, "5.00");

        when(ledgerEntryRepository.findByIdForUpdate(originalEntryId)).thenReturn(Optional.of(original));
        when(ledgerEntryRepository.findByIdempotencyKey("idem-6")).thenReturn(Optional.empty());
        when(ledgerLineRepository.findAllByEntryIdOrderById(originalEntryId)).thenReturn(List.of(debitLine, creditLine));
        when(walletRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(walletA, walletB));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerLineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerEntryResponse response = ledgerService.reverseEntry(
                originalEntryId,
                EntryType.REVERSAL,
                "idem-6",
                "Reverse original",
                metadata()
        );

        assertEquals(EntryType.REVERSAL, response.getEntryType());
        assertEquals(EntryStatus.POSTED, response.getStatus());
        assertEquals(originalEntryId, response.getReversalOfEntryId());
        assertEquals(new BigDecimal("25.00"), walletA.getBalance());
        assertEquals(new BigDecimal("5.00"), walletB.getBalance());
    }

    @Test
    void postPendingShouldMoveEntryToPostedAndApplyBalances() {
        UUID entryId = UUID.randomUUID();
        Wallet debitWallet = wallet(UUID.randomUUID(), CurrencyCode.USD, "100.00");
        Wallet creditWallet = wallet(UUID.randomUUID(), CurrencyCode.USD, "3.00");

        LedgerEntry entry = new LedgerEntry();
        entry.setId(entryId);
        entry.setStatus(EntryStatus.PENDING);
        entry.setCurrency(CurrencyCode.USD);
        entry.setEntryType(EntryType.AUTHORIZATION);
        entry.setIdempotencyKey("idem-7");
        entry.setRequestHash("hash");
        entry.setDescription("pending");
        entry.setCorrelationId("corr");
        entry.setRequestId("req");
        entry.setActor("actor");
        entry.setSource("source");
        entry.setCreatedAt(Instant.now());

        LedgerLine debitLine = persistedLine(entryId, debitWallet.getId(), PostingDirection.DEBIT, "2.00");
        LedgerLine creditLine = persistedLine(entryId, creditWallet.getId(), PostingDirection.CREDIT, "2.00");

        when(ledgerEntryRepository.findByIdForUpdate(entryId)).thenReturn(Optional.of(entry));
        when(ledgerLineRepository.findAllByEntryIdOrderById(entryId)).thenReturn(List.of(debitLine, creditLine));
        when(walletRepository.findAllByIdInForUpdate(any())).thenReturn(List.of(creditWallet, debitWallet));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LedgerEntryResponse response = ledgerService.postPending(entryId);

        assertEquals(EntryStatus.POSTED, response.getStatus());
        assertEquals(new BigDecimal("98.00"), debitWallet.getBalance());
        assertEquals(new BigDecimal("5.00"), creditWallet.getBalance());
    }

    @Test
    void voidPendingShouldMoveEntryToVoided() {
        UUID entryId = UUID.randomUUID();
        LedgerEntry entry = new LedgerEntry();
        entry.setId(entryId);
        entry.setStatus(EntryStatus.PENDING);
        entry.setCurrency(CurrencyCode.USD);
        entry.setEntryType(EntryType.AUTHORIZATION);
        entry.setIdempotencyKey("idem-8");
        entry.setRequestHash("hash");
        entry.setDescription("pending");
        entry.setCorrelationId("corr");
        entry.setRequestId("req");
        entry.setActor("actor");
        entry.setSource("source");
        entry.setCreatedAt(Instant.now());

        LedgerLine line = persistedLine(entryId, UUID.randomUUID(), PostingDirection.DEBIT, "1.00");

        when(ledgerEntryRepository.findByIdForUpdate(entryId)).thenReturn(Optional.of(entry));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerLineRepository.findAllByEntryIdOrderById(entryId)).thenReturn(List.of(line));

        LedgerEntryResponse response = ledgerService.voidPending(entryId);

        assertEquals(EntryStatus.VOIDED, response.getStatus());
        assertNotNull(response.getVoidedAt());
        verify(walletRepository, never()).saveAll(any());
    }

    private PostLedgerEntryRequest baseRequest(EntryType type, EntryStatus status, List<LedgerLineRequest> lines) {
        PostLedgerEntryRequest request = new PostLedgerEntryRequest();
        request.setEntryType(type);
        request.setStatus(status);
        request.setCurrency(CurrencyCode.USD);
        request.setDescription("ledger-entry");
        request.setExternalReference("external-ref");
        request.setLines(lines);
        return request;
    }

    private LedgerRequestMetadata metadata() {
        return new LedgerRequestMetadata("request", "correlation", "actor", "source");
    }

    private LedgerLineRequest line(UUID walletId, PostingDirection direction, String amount) {
        LedgerLineRequest request = new LedgerLineRequest();
        request.setWalletId(walletId);
        request.setDirection(direction);
        request.setAmount(new BigDecimal(amount));
        return request;
    }

    private Wallet wallet(UUID id, CurrencyCode currency, String balance) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setCurrency(currency);
        wallet.setBalance(new BigDecimal(balance));
        wallet.setCreatedAt(Instant.now());
        wallet.setUpdatedAt(Instant.now());
        return wallet;
    }

    private LedgerLine persistedLine(UUID entryId, UUID walletId, PostingDirection direction, String amount) {
        LedgerLine line = new LedgerLine();
        line.setEntryId(entryId);
        line.setWalletId(walletId);
        line.setDirection(direction);
        line.setAmount(new BigDecimal(amount));
        line.setCurrency(CurrencyCode.USD);
        line.setCreatedAt(Instant.now());
        return line;
    }

    private String hash(String payload) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
