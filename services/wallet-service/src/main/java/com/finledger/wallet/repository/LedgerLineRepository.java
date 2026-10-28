package com.finledger.wallet.repository;

import com.finledger.wallet.model.LedgerLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LedgerLineRepository extends JpaRepository<LedgerLine, UUID> {
    List<LedgerLine> findAllByEntryIdOrderById(UUID entryId);
}
