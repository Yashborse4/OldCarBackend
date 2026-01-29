package com.carselling.oldcar.service.car;

import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarStatus;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing dealer inventory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final CarRepository carRepository;

    /**
     * Get inventory for a dealer with filtering
     */
    @Transactional(readOnly = true)
    public Page<Car> getInventory(Long dealerId, String statusFilter, Pageable pageable) {
        log.debug("Fetching inventory for dealer {} with status {}", dealerId, statusFilter);

        Specification<Car> spec = (root, query, cb) -> {
            var predicate = cb.equal(root.get("owner").get("id"), dealerId);

            if (statusFilter != null && !statusFilter.isEmpty()) {
                switch (statusFilter.toUpperCase()) {
                    case "ACTIVE":
                    case "PUBLISHED":
                        predicate = cb.and(predicate, cb.equal(root.get("isActive"), true));
                        predicate = cb.and(predicate, cb.equal(root.get("status"), CarStatus.PUBLISHED));
                        break;
                    case "SOLD":
                        predicate = cb.and(predicate, cb.or(
                                cb.equal(root.get("isSold"), true),
                                cb.equal(root.get("status"), CarStatus.SOLD)));
                        break;
                    case "INACTIVE":
                        // Draft, Archived, or just not active
                        predicate = cb.and(predicate, cb.equal(root.get("isActive"), false));
                        // Exclude sold from inactive if we want strict separation, but usually inactive
                        // implies not sold?
                        // Actually sold cars might be inactive too. Let's assume Inactive = !Active &&
                        // !Sold for now, or just !Active.
                        // Let's stick to simple logic: !Active
                        break;
                    case "DRAFT":
                        predicate = cb.and(predicate, cb.equal(root.get("status"), CarStatus.DRAFT));
                        break;
                    case "ARCHIVED":
                        predicate = cb.and(predicate, cb.equal(root.get("status"), CarStatus.ARCHIVED));
                        break;

                    default:
                        // No specific status filter, return all (maybe exclude deleted)
                        predicate = cb.and(predicate, cb.notEqual(root.get("status"), CarStatus.DELETED));
                        break;
                }
            } else {
                predicate = cb.and(predicate, cb.notEqual(root.get("status"), CarStatus.DELETED));
            }

            return predicate;
        };

        return carRepository.findAll(spec, pageable);
    }

    /**
     * Update car status
     */
    @Transactional
    public void updateCarStatus(Long carId, Long dealerId, String newStatus) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        if (!car.isOwnedBy(dealerId)) {
            throw new UnauthorizedActionException("You do not own this car");
        }

        log.info("Updating status for car {} to {}", carId, newStatus);

        try {
            CarStatus status = CarStatus.valueOf(newStatus.toUpperCase());
            car.setStatus(status);

            // Sync legacy boolean flags
            switch (status) {
                case PUBLISHED:
                    car.setIsActive(true);
                    car.setIsSold(false);
                    break;
                case SOLD:
                    car.setIsActive(false);
                    car.setIsSold(true);
                    break;
                case DRAFT:
                case ARCHIVED:
                    car.setIsActive(false);
                    car.setIsSold(false);
                    break;
                default:
                    break;
            }

            carRepository.save(car);

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + newStatus);
        }
    }
}
