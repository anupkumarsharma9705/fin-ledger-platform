package com.finledger.wallet.service;

import com.finledger.wallet.dto.CreateWalletRequest;
import com.finledger.wallet.dto.CreateWalletResponse;
import com.finledger.wallet.dto.WalletBalanceResponse;
import com.finledger.wallet.exception.ApiException;
import com.finledger.wallet.model.CurrencyCode;
import com.finledger.wallet.model.Wallet;
import com.finledger.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void createWalletShouldSucceed() {
        UUID userId = UUID.randomUUID();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(userId);
        request.setInitialBalance(new BigDecimal("100.00"));
        request.setCurrency(CurrencyCode.INR);

        Wallet savedWallet = new Wallet();
        savedWallet.setId(UUID.randomUUID());
        savedWallet.setUserId(userId);
        savedWallet.setBalance(new BigDecimal("100.00"));
        savedWallet.setCurrency(CurrencyCode.INR);

        when(walletRepository.existsByUserId(userId)).thenReturn(false);
        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

        CreateWalletResponse response = walletService.createWallet(request);

        assertEquals(userId, response.getUserId());
        assertEquals(new BigDecimal("100.00"), response.getBalance());
        assertEquals("INR", response.getCurrency());
    }

    @Test
    void createWalletShouldFailWhenAlreadyExists() {
        UUID userId = UUID.randomUUID();

        CreateWalletRequest request = new CreateWalletRequest();
        request.setUserId(userId);
        request.setInitialBalance(BigDecimal.ZERO);
        request.setCurrency(CurrencyCode.USD);

        when(walletRepository.existsByUserId(userId)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> walletService.createWallet(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void getWalletBalanceShouldSucceed() {
        UUID userId = UUID.randomUUID();

        Wallet wallet = new Wallet();
        wallet.setId(UUID.randomUUID());
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("12.50"));
        wallet.setCurrency(CurrencyCode.EUR);

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletBalanceResponse response = walletService.getWalletBalance(userId);

        assertEquals(userId, response.getUserId());
        assertEquals(new BigDecimal("12.50"), response.getBalance());
        assertEquals("EUR", response.getCurrency());
    }

    @Test
    void getWalletBalanceShouldFailWhenMissing() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> walletService.getWalletBalance(userId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
