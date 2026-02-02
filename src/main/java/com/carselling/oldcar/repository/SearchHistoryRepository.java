package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.SearchHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    @Query("SELECT s.query FROM SearchHistory s GROUP BY s.query ORDER BY COUNT(s) DESC")
    List<String> findTrendingSearches(Pageable pageable);

    @Query("SELECT DISTINCT s.query FROM SearchHistory s WHERE s.user.id = :userId ORDER BY s.createdAt DESC")
    List<String> findRecentSearchesByUser(@Param("userId") Long userId, Pageable pageable);
}
