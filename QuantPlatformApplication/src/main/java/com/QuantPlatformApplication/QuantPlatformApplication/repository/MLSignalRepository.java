package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MLSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MLSignalRepository extends JpaRepository<MLSignal, Long> {
    List<MLSignal> findBySymbolOrderByCreatedAtDesc(String symbol);

    List<MLSignal> findTop10ByOrderByCreatedAtDesc();
}
