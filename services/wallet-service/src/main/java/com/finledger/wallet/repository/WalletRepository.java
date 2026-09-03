package com.finledger.wallet.repository;

import com.finledger.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    boolean existsByUserId(UUID userId);

    Optional<Wallet> findByUserId(UUID userId);
}
