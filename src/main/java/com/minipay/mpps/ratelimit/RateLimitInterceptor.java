package com.minipay.mpps.ratelimit;

import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitingService rateLimitingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // We use the client's IP address as the unique identifier
        String clientIp = request.getRemoteAddr();
        Bucket bucket = rateLimitingService.resolveBucket(clientIp);

        // Try to consume 1 token
        if (bucket.tryConsume(1)) {
            return true; // Token consumed, allow the request!
        } else {
            // Bucket is empty, reject the request!
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Too many requests. Please try again later.");
            return false;
        }
    }
}