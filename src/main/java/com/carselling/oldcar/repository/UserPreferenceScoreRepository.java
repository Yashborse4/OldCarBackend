package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UserPreferenceScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserPreferenceScoreRepository extends JpaRepository<UserPreferenceScore, Long> {

        List<UserPreferenceScore> findByUserIdOrderByScoreDesc(Long userId);

        List<UserPreferenceScore> findByUserIdAndAttributeTypeOrderByScoreDesc(Long userId,
                        UserPreferenceScore.AttributeType attributeType);

        Optional<UserPreferenceScore> findByUserIdAndAttributeTypeAndAttributeValue(Long userId,
                        UserPreferenceScore.AttributeType attributeType, String attributeValue);

        List<UserPreferenceScore> findByAttributeTypeAndAttributeValue(UserPreferenceScore.AttributeType attributeType,
                        String attributeValue);

        @Modifying
        @Query("DELETE FROM UserPreferenceScore u WHERE u.userId = :userId")
        void deleteByUserId(Long userId);

        @Modifying
        @Query("UPDATE UserPreferenceScore u SET u.score = u.score * :decayFactor, u.updatedAt = CURRENT_TIMESTAMP WHERE u.updatedAt < :cutoff")
        int decayOldScores(LocalDateTime cutoff, double decayFactor);

        @Modifying
        @Query("DELETE FROM UserPreferenceScore u WHERE u.score < :threshold")
        int deleteScoresBelowThreshold(double threshold);
}
