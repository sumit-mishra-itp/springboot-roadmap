package dev.roadmap.reactive.price;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The annotated-controller style. Each method returns a recipe (a Mono or Flux), and WebFlux subscribes to it. */
@RestController
@RequestMapping("/prices")
public class PriceController {

    private final PriceService prices;

    public PriceController(PriceService prices) {
        this.prices = prices;
    }

    @PostMapping
    public Mono<ResponseEntity<PriceResponse>> record(@Valid @RequestBody PriceRequest request) {
        return prices.record(request)
                .map(saved -> ResponseEntity.created(URI.create("/prices/" + saved.id())).body(saved));
    }

    @GetMapping("/{id}")
    public Mono<PriceResponse> find(@PathVariable long id) {
        return prices.find(id);
    }

    /** Constraints on parameters trigger the framework's built-in method validation (no @Validated, no AOP proxy). */
    @GetMapping
    public Flux<PriceResponse> history(@RequestParam @Pattern(regexp = "[A-Z]{1,10}") String symbol,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return prices.history(symbol, page, size);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<PriceResponse>> stream(@RequestParam @Pattern(regexp = "[A-Z]{1,10}") String symbol) {
        return prices.stream(symbol);
    }
}
