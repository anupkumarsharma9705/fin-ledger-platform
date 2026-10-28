package com.finledger.wallet.dto;

import com.finledger.wallet.model.PostingDirection;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class LedgerLineRequest {

    @NotNull
    private UUID walletId;

    @NotNull
    private PostingDirection direction;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 17, fraction = 2)
    private BigDecimal amount;

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public PostingDirection getDirection() {
        return direction;
    }

    public void setDirection(PostingDirection direction) {
        this.direction = direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
