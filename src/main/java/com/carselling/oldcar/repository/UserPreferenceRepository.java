package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for UserPreference entity operations.
 */
@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /**
     * Find preferences by user ID.
     *
     * @param userId the user's ID
     * @return optional user preference
     */
    Optional<UserPreference> findByUserId(Long userId);

    /**
     * Check if preferences exist for a user.
     *
     * @param userId the user's ID
     * @return true if preferences exist
     */
    boolean existsByUserId(Long userId);

    /**
     * Delete preferences by user ID.
     *
     * @param userId the user's ID
     */
    void deleteByUserId(Long userId);
}
