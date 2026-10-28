package com.finledger.wallet.dto;

import com.finledger.wallet.model.CurrencyCode;
import com.finledger.wallet.model.EntryStatus;
import com.finledger.wallet.model.EntryType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PostLedgerEntryRequest {

    @NotNull
    private EntryType entryType;

    @NotNull
    private EntryStatus status;

    @NotNull
    private CurrencyCode currency;

    @NotBlank
    private String description;

    private String externalReference;

    @NotEmpty
    private List<@Valid LedgerLineRequest> lines;

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public void setStatus(EntryStatus status) {
        this.status = status;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public List<LedgerLineRequest> getLines() {
        return lines;
    }

    public void setLines(List<LedgerLineRequest> lines) {
        this.lines = lines;
    }
}
