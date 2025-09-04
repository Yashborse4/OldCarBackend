package com.carselling.oldcar.dto.graphql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GraphQL AuthPayload DTO for GraphQL authentication responses
 * Maps to the AuthPayload type in schema.graphqls
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthPayload {

    private String token;
    private String type;
    private String id;
    private String email;
    private String role;
}
