package dev.roadmap.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.roadmap.shop.catalog.ProductResponse;
import dev.roadmap.shop.common.ApiExceptionHandler;
import dev.roadmap.shop.order.OrderResponse;
import dev.roadmap.shop.order.PlaceOrderRequest;
import dev.roadmap.shop.order.PlaceOrderRequest.Line;

/** Order placement, its failure modes, ownership, and the CUSTOMER rule enforced by method security. */
class OrderApiTest extends ApiTestSupport {

    String admin;
    long categoryId;

    @BeforeEach
    void catalog() {
        admin = adminToken();
        categoryId = createCategory(admin);
    }

    @Test
    void placeOrder_decrementsStock_snapshotsThePrice_andReturns201WithLocation() {
        ProductResponse keyboard = createProduct(admin, categoryId, "10.50", 5);
        Customer customer = newCustomer();

        OrderResponse order = client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .headers(bearer(customer.accessToken()))
                .body(new PlaceOrderRequest(List.of(new Line(keyboard.id(), 2))))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueMatches("Location", ".*/api/orders/\\d+")
                .expectBody(OrderResponse.class).returnResult().getResponseBody();

        assertThat(order.total()).isEqualByComparingTo("21.00");
        assertThat(order.lines()).singleElement().satisfies(line -> {
            assertThat(line.productId()).isEqualTo(keyboard.id());
            assertThat(line.unitPrice()).isEqualByComparingTo("10.50");
        });
        ProductResponse after = product(keyboard.id());
        assertThat(after.stock()).isEqualTo(3);
        assertThat(after.version()).isEqualTo(keyboard.version() + 1); // the stock UPDATE carried the version check
    }

    @Test
    void placeOrder_withInsufficientStock_returns409_andRollsBackEveryLine() {
        ProductResponse plenty = createProduct(admin, categoryId, "1.00", 5);
        ProductResponse scarce = createProduct(admin, categoryId, "1.00", 2);
        Customer customer = newCustomer();

        place(customer, new Line(plenty.id(), 1), new Line(scarce.id(), 10))
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "insufficient-stock")
                .jsonPath("$.productId").isEqualTo(scarce.id())
                .jsonPath("$.requested").isEqualTo(10)
                .jsonPath("$.available").isEqualTo(2);

        assertThat(product(plenty.id()).stock()).as("first line rolled back too").isEqualTo(5);
        assertThat(product(scarce.id()).stock()).isEqualTo(2);
    }

    @Test
    void placeOrder_unknownProduct_returns404Problem() {
        place(newCustomer(), new Line(999_999L, 1))
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Product 999999 not found");
    }

    @Test
    void placeOrder_invalidBody_returns400WithFieldErrors() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).headers(bearer(newCustomer().accessToken()))
                .body("""
                        {"lines":[{"productId":1,"quantity":0}]}""")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("lines[0].quantity");
    }

    @Test
    void placeOrder_withAdminToken_isForbiddenByMethodSecurity() {
        // The URL rule for /api/orders/** only says authenticated(). The 403 comes from @PreAuthorize on OrderService:
        // remove @EnableMethodSecurity and this test fails with a 201.
        ProductResponse product = createProduct(admin, categoryId, "1.00", 5);

        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).headers(bearer(admin))
                .body(new PlaceOrderRequest(List.of(new Line(product.id(), 1))))
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "forbidden");
        assertThat(product(product.id()).stock()).isEqualTo(5);
    }

    @Test
    void orders_withoutToken_return401() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void customersSeeOnlyTheirOwnOrders() {
        ProductResponse product = createProduct(admin, categoryId, "3.00", 10);
        Customer alice = newCustomer();
        Customer bob = newCustomer();
        long alicesOrder = place(alice, new Line(product.id(), 1)).expectStatus().isCreated()
                .expectBody(OrderResponse.class).returnResult().getResponseBody().id();

        client.get().uri("/api/orders/{id}", alicesOrder).headers(bearer(alice.accessToken())).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.lines[0].productId").isEqualTo(product.id());
        client.get().uri("/api/orders").headers(bearer(alice.accessToken())).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1).jsonPath("$.content[0].id").isEqualTo(alicesOrder);

        // For Bob, Alice's order does not exist: 404, not 403, so order ids can't be probed.
        client.get().uri("/api/orders/{id}", alicesOrder).headers(bearer(bob.accessToken())).exchange()
                .expectStatus().isNotFound();
        client.get().uri("/api/orders").headers(bearer(bob.accessToken())).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
    }

    private RestTestClient.ResponseSpec place(Customer customer, Line... lines) {
        return client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .headers(bearer(customer.accessToken()))
                .body(new PlaceOrderRequest(List.of(lines)))
                .exchange();
    }

    private ProductResponse product(long id) {
        return client.get().uri("/api/products/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
    }
}
