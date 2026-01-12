package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CarMasterRepository extends JpaRepository<CarMaster, Long> {

    @Query("SELECT DISTINCT c.model FROM CarMaster c WHERE c.make = :make AND LOWER(c.model) LIKE LOWER(CONCAT(:query, '%')) ORDER BY c.model")
    List<String> findDistinctModelsByMakeAndQuery(@Param("make") String make, @Param("query") String query);

    @Query("SELECT DISTINCT c.model FROM CarMaster c WHERE c.make = :make ORDER BY c.model")
    List<String> findDistinctModelsByMake(@Param("make") String make);

    List<CarMaster> findByMakeAndModel(String make, String model);
}
