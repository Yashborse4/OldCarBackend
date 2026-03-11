package com.carselling.oldcar.model.listener;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarAuditLog;
import com.carselling.oldcar.repository.CarAuditLogRepository;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

@Slf4j
@Component
public class CarAuditListener {

    private static ObjectProvider<CarAuditLogRepository> auditLogRepositoryProvider;

    @Autowired
    public void setAuditLogRepositoryProvider(ObjectProvider<CarAuditLogRepository> provider) {
        CarAuditListener.auditLogRepositoryProvider = provider;
    }

    @PreUpdate
    public void preUpdate(Car car) {
        try {
            boolean priceChanged = false;
            boolean mileageChanged = false;

            BigDecimal oldPrice = car.getOriginalPrice();
            BigDecimal newPrice = car.getPrice();
            if (oldPrice != null && newPrice != null && oldPrice.compareTo(newPrice) != 0) {
                priceChanged = true;
            }

            Integer oldMileage = car.getOriginalMileage();
            Integer newMileage = car.getMileage();
            if (!Objects.equals(oldMileage, newMileage) && oldMileage != null) {
                mileageChanged = true;
            }

            if (priceChanged || mileageChanged) {
                log.info("Detected change in Car {} - Price: {} -> {}, Mileage: {} -> {}",
                        car.getId(), oldPrice, newPrice, oldMileage, newMileage);

                if (auditLogRepositoryProvider != null) {
                    CarAuditLogRepository repository = auditLogRepositoryProvider.getIfAvailable();
                    if (repository != null) {
                        Long currentUserId = SecurityUtils.getCurrentUserId();
                        String changedBy = currentUserId != null ? String.valueOf(currentUserId) : "SYSTEM";

                        CarAuditLog auditLog = CarAuditLog.builder()
                                .carId(car.getId())
                                .oldPrice(oldPrice)
                                .newPrice(newPrice)
                                .oldMileage(oldMileage)
                                .newMileage(newMileage)
                                .changedBy(changedBy)
                                .build();
                        repository.save(auditLog);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process audit log for car {}", car.getId(), e);
        }
    }
}
