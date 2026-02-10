package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.analytics.LeadDto;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.DealerAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dealer/analytics")
@RequiredArgsConstructor
@Slf4j
public class DealerAnalyticsController {

    private final DealerAnalyticsService dealerAnalyticsService;

    @GetMapping("/leads")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get Dealer Leads", description = "Get list of users who have viewed dealer's cars, sorted by interest")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Slice<LeadDto>>> getDealerLeads(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PageableDefault(size = 20) Pageable pageable) {

        org.springframework.data.domain.Slice<LeadDto> leads = dealerAnalyticsService
                .getDealerLeads(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Leads retrieved successfully", leads));
    }
}
