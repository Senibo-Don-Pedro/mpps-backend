package com.minipay.mpps.common.config;

import com.minipay.mpps.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply the rate limiter only to our API endpoints
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}