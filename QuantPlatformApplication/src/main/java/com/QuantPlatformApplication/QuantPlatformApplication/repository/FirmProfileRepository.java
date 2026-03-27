package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.FirmProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * JPA repository for {@link FirmProfile} entities.
 */
public interface FirmProfileRepository extends JpaRepository<FirmProfile, Long> {

    /**
     * Find the firm profile owned by a specific user.
     *
     * @param userId the owner user ID
     * @return the firm profile if it exists
     */
    Optional<FirmProfile> findByOwnerUserId(Long userId);
}
