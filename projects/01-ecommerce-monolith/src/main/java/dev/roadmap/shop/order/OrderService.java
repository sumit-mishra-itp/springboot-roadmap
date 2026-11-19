package dev.roadmap.shop.order;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.roadmap.shop.catalog.Product;
import dev.roadmap.shop.catalog.ProductRepository;
import dev.roadmap.shop.common.NotFoundException;

/**
 * Method security is the only CUSTOMER check (the URL rule just says "authenticated"), so an admin token gets a 403
 * from here. customerId is always the caller's own id, taken from the token by the controller.
 */
@Service
@PreAuthorize("hasRole('CUSTOMER')")
public class OrderService {

    private final OrderRepository orders;
    private final ProductRepository products;

    public OrderService(OrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    /**
     * One transaction: read the products, decrement stock in memory, save the order. At commit Hibernate flushes the
     * INSERTs, then "UPDATE products ... WHERE id = ? AND version = ?". If another order changed the product after we
     * read it, that UPDATE matches 0 rows, everything rolls back, and the caller gets a 409 concurrent-update.
     * No retry here on purpose: the client decides whether to retry (see the README).
     */
    @Transactional
    public OrderResponse place(long customerId, PlaceOrderRequest request) {
        Map<Long, Product> byId = productsIn(request);
        Order order = new Order(customerId, Instant.now());
        for (PlaceOrderRequest.Line line : request.lines()) {
            Product product = byId.get(line.productId());
            if (product == null) {
                throw new NotFoundException("Product", line.productId());
            }
            product.decrementStock(line.quantity());
            order.addLine(product.getId(), line.quantity(), product.getPrice());
        }
        return OrderResponse.from(orders.save(order));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(long customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long customerId, long orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /** One SELECT ... WHERE id IN (...) for all lines, instead of one query per line. */
    private Map<Long, Product> productsIn(PlaceOrderRequest request) {
        var ids = request.lines().stream().map(PlaceOrderRequest.Line::productId).collect(Collectors.toSet());
        return products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    }
}
