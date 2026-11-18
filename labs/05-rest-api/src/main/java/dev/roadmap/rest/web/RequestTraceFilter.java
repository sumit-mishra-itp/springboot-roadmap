package dev.roadmap.rest.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet Filter: runs OUTSIDE Spring MVC, around the DispatcherServlet, for every request (static files and
 * error dispatches too). Its real job here is a correlation id (X-Request-Id plus the SLF4J MDC). For the lesson it
 * also appends to a per-request "trace" list so a test can assert the exact invocation order.
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ATTRIBUTE = "trace";
    public static final String REQUEST_ID = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(TRACE_ATTRIBUTE, new ArrayList<String>());
        String requestId = request.getHeader(REQUEST_ID) != null ? request.getHeader(REQUEST_ID) : UUID.randomUUID().toString();
        response.setHeader(REQUEST_ID, requestId); // set BEFORE the chain: once the body is written, headers are frozen
        MDC.put("requestId", requestId);
        record(request, "filter.before");
        try {
            chain.doFilter(request, response);
        }
        finally {
            record(request, "filter.after");
            MDC.remove("requestId"); // pooled (or reused) threads would otherwise leak the id into the next request
        }
    }

    @SuppressWarnings("unchecked")
    static void record(HttpServletRequest request, String event) {
        if (request.getAttribute(TRACE_ATTRIBUTE) instanceof List<?> trace) {
            ((List<String>) trace).add(event);
        }
    }
}
