package com.carselling.oldcar.specification;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.model.Car;

import com.carselling.oldcar.model.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CarSpecification {

    public static Specification<Car> getCarsByCriteria(CarSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always filter by active
            predicates.add(criteriaBuilder.isTrue(root.get("isActive")));

            if (criteria == null) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }

            // JOIN for Owner fields
            Join<Car, User> ownerJoin = root.join("owner", JoinType.LEFT);

            // 1. Text Search (Query) - Applies to Make, Model, or Description
            if (StringUtils.hasText(criteria.getQuery())) {
                String searchTerm = "%" + criteria.getQuery().trim().toLowerCase() + "%";
                Predicate makeLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("make")), searchTerm);
                Predicate modelLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("model")), searchTerm);
                Predicate descLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchTerm);
                predicates.add(criteriaBuilder.or(makeLike, modelLike, descLike));
            }

            // 2. Exact/Partial Filters

            if (StringUtils.hasText(criteria.getMake())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("make")),
                        "%" + criteria.getMake().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(criteria.getModel())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("model")),
                        "%" + criteria.getModel().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(criteria.getFuelType())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fuelType")),
                        "%" + criteria.getFuelType().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(criteria.getTransmission())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("transmission")),
                        "%" + criteria.getTransmission().trim().toLowerCase() + "%"));
            }

            if (StringUtils.hasText(criteria.getLocation())) {
                String locTerm = "%" + criteria.getLocation().trim().toLowerCase() + "%";
                Predicate carLoc = criteriaBuilder.like(criteriaBuilder.lower(root.get("location")), locTerm);
                Predicate ownerLoc = criteriaBuilder.like(criteriaBuilder.lower(ownerJoin.get("location")), locTerm);
                predicates.add(criteriaBuilder.or(carLoc, ownerLoc));
            }

            // 3. Range Filters

            if (criteria.getMinYear() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("year"), criteria.getMinYear()));
            }

            if (criteria.getMaxYear() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("year"), criteria.getMaxYear()));
            }

            if (criteria.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"),
                        BigDecimal.valueOf(criteria.getMinPrice())));
            }

            if (criteria.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"),
                        BigDecimal.valueOf(criteria.getMaxPrice())));
            }

            if (criteria.getMinMileage() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("mileage"), criteria.getMinMileage()));
            }

            if (criteria.getMaxMileage() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("mileage"), criteria.getMaxMileage()));
            }

            // 4. Status Filters

            if (StringUtils.hasText(criteria.getStatus())) {
                String status = criteria.getStatus().trim().toUpperCase();
                if ("SOLD".equals(status)) {
                    predicates.add(criteriaBuilder.isTrue(root.get("isSold")));
                } else if ("AVAILABLE".equals(status) || "ACTIVE".equals(status)) {
                    predicates.add(criteriaBuilder.isFalse(root.get("isSold")));
                }
            }

            // 5. Featured Filter
            if (Boolean.TRUE.equals(criteria.getFeatured())) {
                predicates.add(criteriaBuilder.isTrue(root.get("isFeatured")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
