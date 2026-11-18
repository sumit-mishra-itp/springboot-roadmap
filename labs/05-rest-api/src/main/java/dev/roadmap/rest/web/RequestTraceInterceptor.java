package dev.roadmap.rest.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.Nullable;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * HandlerInterceptor: runs INSIDE the DispatcherServlet, only for requests that mapped to a handler, and knows
 * WHICH handler (the handler argument is the HandlerMethod). Registered in WebConfig with path patterns.
 * Records two facts the order test asserts:
 * - postHandle on a @RestController sees an already committed response, because the body was written earlier;
 * - afterCompletion gets ex == null when an @ExceptionHandler already resolved the exception.
 */
public class RequestTraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequestTraceFilter.record(request, "interceptor.preHandle");
        return true; // false would stop the chain here: no controller, no postHandle
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) {
        RequestTraceFilter.record(request, "interceptor.postHandle committed=" + response.isCommitted());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) {
        RequestTraceFilter.record(request, "interceptor.afterCompletion ex=" + (ex == null ? "null" : ex.getClass().getSimpleName()));
    }
}
