package dev.roadmap.observability.tracing;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Calls this same app over HTTP. It is built from the auto-configured RestClient.Builder, which carries
 * the ObservationRegistry: that is what creates the client span and injects the traceparent header.
 * RestClient.create() would compile and run fine but propagate nothing.
 */
@Component
class SelfCallClient {

    private final RestClient restClient;

    SelfCallClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    String fetchEchoedTraceparent() {
        // Resolve the base URL per request, because the random port is unknown at construction time.
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        return restClient.get().uri(base + "/trace/echo").retrieve().body(String.class);
    }
}
