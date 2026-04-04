package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "distributed_cache_entries", indexes = {
        @Index(name = "idx_cache_expiry", columnList = "expiry_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DistributedCacheEntry {

    @Id
    @Column(name = "cache_key", length = 255, nullable = false)
    private String key;

    @Column(name = "value_payload", columnDefinition = "TEXT")
    private String valuePayload;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @UpdateTimestamp
    @Column(name = "last_accessed")
    private LocalDateTime lastAccessed;

    public boolean isExpired() {
        return expiryTime != null && LocalDateTime.now().isAfter(expiryTime);
    }
}
