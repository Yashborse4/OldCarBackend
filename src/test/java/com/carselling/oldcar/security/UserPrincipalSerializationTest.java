package com.carselling.oldcar.security;

import com.carselling.oldcar.model.Role;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that UserPrincipal can be serialized and deserialized using the same
 * ObjectMapper configuration as RedisConfig, ensuring Redis cache round-trips work.
 */
class UserPrincipalSerializationTest {

    private Jackson2JsonRedisSerializer<Object> serializer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModules(
                org.springframework.security.jackson2.SecurityJackson2Modules
                        .getModules(getClass().getClassLoader()));
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        // Use constructor-based ObjectMapper injection (matches the fix in RedisConfig)
        serializer = new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
    }

    @Test
    void shouldSerializeAndDeserializeUserPrincipal() throws Exception {
        UserPrincipal original = new UserPrincipal(
                1L, "test@example.com", "hashedpass",
                Role.USER, true, true, false, null);

        byte[] serialized = serializer.serialize(original);
        assertNotNull(serialized);

        Object deserialized = serializer.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(UserPrincipal.class, deserialized.getClass());

        UserPrincipal result = (UserPrincipal) deserialized;
        assertEquals(original.getId(), result.getId());
        assertEquals(original.getEmail(), result.getEmail());
        assertEquals(original.getRole(), result.getRole());
        assertEquals(original.isActive(), result.isActive());
        assertEquals(original.isEmailVerified(), result.isEmailVerified());
    }

    @Test
    void shouldSerializeAndDeserializeUserPrincipalWithLockedUntil() throws Exception {
        LocalDateTime lockedUntil = LocalDateTime.of(2026, 12, 31, 23, 59, 59);
        UserPrincipal original = new UserPrincipal(
                42L, "dealer@example.com", "pass",
                Role.DEALER, true, true, true, lockedUntil);

        byte[] serialized = serializer.serialize(original);
        assertNotNull(serialized);

        Object deserialized = serializer.deserialize(serialized);
        assertNotNull(deserialized);
        assertEquals(UserPrincipal.class, deserialized.getClass());

        UserPrincipal result = (UserPrincipal) deserialized;
        assertEquals(42L, result.getId());
        assertEquals("dealer@example.com", result.getEmail());
        assertEquals(Role.DEALER, result.getRole());
        assertEquals(lockedUntil, result.getLockedUntil());
        assertTrue(result.isVerifiedDealer());
    }
}
