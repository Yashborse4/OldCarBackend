package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.DistributedCacheEntry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DistributedCacheRepository extends JpaRepository<DistributedCacheEntry, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DistributedCacheEntry c WHERE c.key = :key")
    Optional<DistributedCacheEntry> findByKeyForUpdate(@Param("key") String key);

    @Modifying
    @Query("DELETE FROM DistributedCacheEntry c WHERE c.expiryTime < :now")
    int deleteExpiredEntries(@Param("now") LocalDateTime now);
}
