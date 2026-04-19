package com.QuantPlatformApplication.QuantPlatformApplication.repository;

import com.QuantPlatformApplication.QuantPlatformApplication.model.entity.MLFeatureSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface MLFeatureSnapshotRepository extends JpaRepository<MLFeatureSnapshot, Long> {

    List<MLFeatureSnapshot> findBySymbolAndTimestampBetweenOrderByTimestampAsc(
            String symbol, Instant from, Instant to);

    List<MLFeatureSnapshot> findTop100BySymbolOrderByTimestampDesc(String symbol);
}
