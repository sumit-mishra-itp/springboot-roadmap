package dev.roadmap.observability.tracing;

import java.util.Optional;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/trace")
class TraceController {

    private static final Logger log = LoggerFactory.getLogger(TraceController.class);

    private final Tracer tracer;
    private final SelfCallClient selfCallClient;

    TraceController(Tracer tracer, SelfCallClient selfCallClient) {
        this.tracer = tracer;
        this.selfCallClient = selfCallClient;
    }

    /** Server span, then a client span for the outgoing call, then a second server span on /trace/echo. */
    @GetMapping("/outbound")
    TraceReport outbound() {
        String traceId = Optional.ofNullable(tracer.currentSpan()).map(Span::context).map(TraceContext::traceId).orElse("none");
        log.info("Calling /trace/echo on myself");
        return new TraceReport(traceId, selfCallClient.fetchEchoedTraceparent());
    }

    /** Returns the W3C trace context header exactly as it arrived. */
    @GetMapping("/echo")
    String echo(@RequestHeader(name = "traceparent", required = false) String traceparent) {
        return traceparent == null ? "absent" : traceparent;
    }

    record TraceReport(String traceId, String echoedTraceparent) {
    }
}
