package com.carselling.oldcar.config;

import com.carselling.oldcar.interceptor.PerformanceInterceptor;
import com.carselling.oldcar.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web configuration to register interceptors
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final PerformanceInterceptor performanceInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register rate limiting interceptor
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                    "/api/health/**",
                    "/api/docs/**",
                    "/api-docs/**",
                    "/swagger-ui/**"
                );

        // Register performance monitoring interceptor
        registry.addInterceptor(performanceInterceptor)
                .addPathPatterns("/api/**");
    }
}
