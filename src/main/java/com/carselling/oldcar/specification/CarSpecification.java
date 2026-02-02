package com.carselling.oldcar.specification;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.model.Car;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class CarSpecification {

    public static Specification<Car> getCarsByCriteria(CarSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            // Optimization: Fetch owner eagerly to avoid N+1 problems
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("owner", jakarta.persistence.criteria.JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            // Always filter by active
            predicates.add(criteriaBuilder.isTrue(root.get("isActive")));

            if (criteria == null) {
                return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
            }

            // 1. Text Search (Query) - "Smart Search" Logic
            if (StringUtils.hasText(criteria.getQuery())) {
                String fullQuery = criteria.getQuery().trim();
                String[] tokens = fullQuery.split("\\s+"); // Split by whitespace

                List<Predicate> tokenPredicates = new ArrayList<>();

                for (String token : tokens) {
                    if (token.length() < 1)
                        continue;
                    String searchTerm = "%" + token.toLowerCase() + "%";

                    List<Predicate> orPredicates = new ArrayList<>();

                    // Match against Make, Model, Variant, Description
                    orPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("make")), searchTerm));
                    orPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("model")), searchTerm));
                    orPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("variant")), searchTerm));
                    orPredicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), searchTerm));

                    // Optional: Match Year if token is numeric
                    if (token.matches("\\d{4}")) {
                        try {
                            int year = Integer.parseInt(token);
                            orPredicates.add(criteriaBuilder.equal(root.get("year"), year));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // Helper: Combine ORs for this token (Any field can match this token)
                    tokenPredicates.add(criteriaBuilder.or(orPredicates.toArray(new Predicate[0])));
                }

                // Combine all Token Predicates with AND (All tokens must find a match)
                if (!tokenPredicates.isEmpty()) {
                    predicates.add(criteriaBuilder.and(tokenPredicates.toArray(new Predicate[0])));
                }
            }

            // 2. Exact/Partial Filters

            if (criteria.getMake() != null && !criteria.getMake().isEmpty()) {
                predicates.add(root.get("make").in(criteria.getMake()));
            }
            if (criteria.getModel() != null && !criteria.getModel().isEmpty()) {
                predicates.add(root.get("model").in(criteria.getModel()));
            }
            // Add variant search if needed
            if (StringUtils.hasText(criteria.getVariant())) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("variant")),
                        "%" + criteria.getVariant().toLowerCase().trim() + "%"));
            }

            if (criteria.getLocation() != null && !criteria.getLocation().isEmpty()) {
                predicates.add(root.get("location").in(criteria.getLocation()));
            }
            if (criteria.getFuelType() != null && !criteria.getFuelType().isEmpty()) {
                predicates.add(root.get("fuelType").in(criteria.getFuelType()));
            }
            if (criteria.getTransmission() != null && !criteria.getTransmission().isEmpty()) {
                predicates.add(root.get("transmission").in(criteria.getTransmission()));
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
