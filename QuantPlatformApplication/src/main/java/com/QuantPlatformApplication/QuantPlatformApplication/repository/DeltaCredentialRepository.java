package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.DeltaCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeltaCredentialRepository extends JpaRepository<DeltaCredential, Long> {
    Optional<DeltaCredential> findByUserIdAndIsTestnet(Long userId, Boolean isTestnet);
    void deleteByUserIdAndIsTestnet(Long userId, Boolean isTestnet);
}
