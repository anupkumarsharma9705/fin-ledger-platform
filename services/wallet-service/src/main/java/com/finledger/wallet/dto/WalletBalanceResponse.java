package com.finledger.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class WalletBalanceResponse {

    private final UUID walletId;
    private final UUID userId;
    private final BigDecimal balance;
    private final String currency;

    public WalletBalanceResponse(UUID walletId, UUID userId, BigDecimal balance, String currency) {
        this.walletId = walletId;
        this.userId = userId;
        this.balance = balance;
        this.currency = currency;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }
}
