package com.carselling.oldcar.dto.admin;

import com.carselling.oldcar.dto.SystemStatistics;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AdminDashboardResponse {
    private SystemStatistics systemStatistics;
    private List<Map<String, String>> quickActions;
    private List<Map<String, Object>> recentActivity;
}
