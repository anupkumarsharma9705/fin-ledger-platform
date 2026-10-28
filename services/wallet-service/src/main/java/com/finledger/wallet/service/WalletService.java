package com.finledger.wallet.service;

import com.finledger.wallet.dto.CreateWalletRequest;
import com.finledger.wallet.dto.CreateWalletResponse;
import com.finledger.wallet.dto.WalletBalanceResponse;
import com.finledger.wallet.exception.ApiException;
import com.finledger.wallet.model.Wallet;
import com.finledger.wallet.repository.WalletRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public CreateWalletResponse createWallet(CreateWalletRequest request) {
        if (walletRepository.existsByUserId(request.getUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Wallet already exists for user");
        }

        Wallet wallet = new Wallet();
        wallet.setUserId(request.getUserId());
        try {
            wallet.setBalance(request.getInitialBalance().setScale(2, RoundingMode.UNNECESSARY));
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Initial balance scale must be 2 decimal places");
        }
        wallet.setCurrency(request.getCurrency());

        Wallet savedWallet;
        try {
            savedWallet = walletRepository.save(wallet);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Wallet already exists for user");
        }
        return new CreateWalletResponse(
                savedWallet.getId(),
                savedWallet.getUserId(),
                savedWallet.getBalance(),
                savedWallet.getCurrency().name()
        );
    }

    @Transactional(readOnly = true)
    public WalletBalanceResponse getWalletBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Wallet not found"));

        return new WalletBalanceResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCurrency().name()
        );
    }
}
