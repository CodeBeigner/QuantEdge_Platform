package com.QuantPlatformApplication.QuantPlatformApplication.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Rate-limiting interceptor using Redis INCR + EXPIRE.
 * Limits each IP to 100 requests per minute.
 * Returns HTTP 429 Too Many Requests when exceeded.
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String clientIp = getClientIp(request);
        String key = "rate_limit:" + clientIp;

        try {
            Long count = redisTemplate.opsForValue().increment(key);

            if (count != null && count == 1) {
                // First request in this window — set expiry
                redisTemplate.expire(key, WINDOW);
            }

            if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
                log.warn("Rate limit exceeded for IP: {} ({} requests)", clientIp, count);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Rate limit exceeded. Max " + MAX_REQUESTS_PER_MINUTE + " requests/minute.\"}");
                return false;
            }
        } catch (Exception e) {
            // If Redis is down, allow the request (fail-open)
            log.warn("Redis unavailable for rate limiting, allowing request: {}", e.getMessage());
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
