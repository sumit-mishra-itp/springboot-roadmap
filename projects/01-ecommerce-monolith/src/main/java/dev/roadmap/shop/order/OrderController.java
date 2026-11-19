package dev.roadmap.shop.order;

import java.net.URI;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import dev.roadmap.shop.common.PageResponse;

/** HTTP only. The customer is always the caller: the JWT "sub" claim (the user id) is Authentication.getName(). */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody PlaceOrderRequest request, Authentication caller,
                                               UriComponentsBuilder uri) {
        OrderResponse placed = service.place(customerId(caller), request);
        URI location = uri.path("/api/orders/{id}").buildAndExpand(placed.id()).toUri();
        return ResponseEntity.created(location).body(placed);
    }

    /** The caller's own orders, newest first by default. */
    @GetMapping
    public PageResponse<OrderSummary> list(Authentication caller,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(service.list(customerId(caller), pageable));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id, Authentication caller) {
        return service.get(customerId(caller), id);
    }

    private static long customerId(Authentication caller) {
        return Long.parseLong(caller.getName());
    }
}
