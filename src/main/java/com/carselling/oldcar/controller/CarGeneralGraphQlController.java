package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.graphql.CarGraphQLDto;
import com.carselling.oldcar.dto.user.UserSummary;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.graphql.CarGraphQlService;
import com.carselling.oldcar.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
@Slf4j
public class CarGeneralGraphQlController {

    private final CarGraphQlService carGraphQlService;

    @QueryMapping
    @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
    public List<CarGraphQLDto> listMyCars() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching cars for user: {}", currentUserId);
        return carGraphQlService.getCarsByDealer(currentUserId.toString());
    }

    // Resolves the 'owner' field for the CarGraphQLDto type
    @SchemaMapping(typeName = "Car", field = "owner")
    public CompletableFuture<UserSummary> owner(CarGraphQLDto car, org.dataloader.DataLoader<Long, User> userLoader) {
        if (car.getOwner() != null && car.getOwner().getId() != null) {
            return userLoader.load(car.getOwner().getId())
                    .thenApply(user -> UserSummary.builder()
                            .id(user.getId())
                            .username(user.getFullName())
                            // .email(user.getEmail()) // Be careful showing email
                            .build());
        }
        return CompletableFuture.completedFuture(null);
    }
}
