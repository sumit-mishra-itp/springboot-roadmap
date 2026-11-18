package dev.roadmap.cache.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate-limits /products/** before the controller runs. The exception thrown here still reaches
 * the @RestControllerAdvice, because preHandle runs inside DispatcherServlet's exception handling.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    static final String CLIENT_HEADER = "X-Client-Id";

    private final RedisRateLimiter limiter;

    public RateLimitInterceptor(RedisRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/products/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RedisRateLimiter.Decision decision = limiter.tryAcquire(clientId(request));
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfter());
        }
        return true;
    }

    // ponytail: trusts a client-supplied header. Behind a gateway, key on the authenticated principal or API key instead.
    private static String clientId(HttpServletRequest request) {
        String header = request.getHeader(CLIENT_HEADER);
        return header != null ? header : request.getRemoteAddr();
    }
}
