package com.synapsex.ingestion.repository;

import com.synapsex.ingestion.model.IngestionApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestionApiKeyRepository extends JpaRepository<IngestionApiKey, UUID> {
    Optional<IngestionApiKey> findByKeyHash(String keyHash);
}
