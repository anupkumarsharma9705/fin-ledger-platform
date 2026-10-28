package com.finledger.wallet.dto;

import com.finledger.wallet.model.PostingDirection;

import java.math.BigDecimal;
import java.util.UUID;

public class LedgerLineResponse {

    private final UUID walletId;
    private final PostingDirection direction;
    private final BigDecimal amount;
    private final String currency;

    public LedgerLineResponse(UUID walletId, PostingDirection direction, BigDecimal amount, String currency) {
        this.walletId = walletId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public PostingDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
