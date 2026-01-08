package com.carselling.oldcar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatistics {

    private long totalUsers;
    private long activeUsers;
    private long dealerCount;
    private long adminCount;
    private long newUsersLast7Days;
}

