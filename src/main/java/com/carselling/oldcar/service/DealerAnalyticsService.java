package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.analytics.LeadDto;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserAnalyticsEventRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Car;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class DealerAnalyticsService {

    private final UserAnalyticsEventRepository analyticsRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    public Slice<LeadDto> getDealerLeads(Long dealerId, Pageable pageable) {
        // 1. Get all car IDs for this dealer (including co-owned)
        List<Long> carIds = carRepository.findCarIdsByOwnerId(dealerId);
        List<String> carIdStrings = carIds.stream().map(String::valueOf).collect(Collectors.toList());

        if (carIdStrings.isEmpty()) {
            return new SliceImpl<>(java.util.Collections.emptyList(), pageable, false);
        }

        // 2. Get raw data from repository as Slice
        Slice<Object[]> results = analyticsRepository.getUserViewCountsForCars(carIdStrings, pageable);

        // 3. Map to LeadDto and safe filter
        List<LeadDto> mappedLeads = results.stream().map(row -> {
            Long userId = null;
            if (row[0] instanceof Long)
                userId = (Long) row[0];
            else if (row[0] instanceof String)
                userId = Long.parseLong((String) row[0]);

            String carIdStr = (String) row[1];
            Long viewCount = (Long) row[2];

            if (userId == null)
                return null;

            User user = userRepository.findById(userId).orElse(null);
            Car car = carRepository.findById(Long.parseLong(carIdStr)).orElse(null);

            if (user != null && car != null) {
                return LeadDto.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                        .avatarUrl(user.getProfileImageUrl())
                        .carId(car.getId())
                        .carTitle(car.getFullName())
                        .carImage(car.getImages().isEmpty() ? car.getImageUrl() : car.getImages().get(0))
                        .carPrice(car.getPrice().toString())
                        .viewCount(viewCount)
                        .interactionCount(0L)
                        .lastActiveAt(null)
                        .interestScore((double) viewCount)
                        .build();
            }
            return null;
        }).filter(java.util.Objects::nonNull).collect(Collectors.toList());

        return new SliceImpl<>(mappedLeads, pageable, results.hasNext());
    }
}
