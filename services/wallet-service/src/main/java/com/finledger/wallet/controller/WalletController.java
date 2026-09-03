package com.finledger.wallet.controller;

import com.finledger.wallet.dto.CreateWalletRequest;
import com.finledger.wallet.dto.CreateWalletResponse;
import com.finledger.wallet.dto.WalletBalanceResponse;
import com.finledger.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateWalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        return walletService.createWallet(request);
    }

    @GetMapping("/{userId}/balance")
    public WalletBalanceResponse getWalletBalance(@PathVariable UUID userId) {
        return walletService.getWalletBalance(userId);
    }
}
