package dev.roadmap.shop;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.roadmap.shop.auth.AuthDtos.LoginRequest;
import dev.roadmap.shop.auth.AuthDtos.RegisterRequest;
import dev.roadmap.shop.auth.AuthDtos.TokenResponse;
import dev.roadmap.shop.auth.AuthDtos.UserResponse;
import dev.roadmap.shop.catalog.CategoryRequest;
import dev.roadmap.shop.catalog.CategoryResponse;
import dev.roadmap.shop.catalog.ProductRequest;
import dev.roadmap.shop.catalog.ProductResponse;

/**
 * Shared setup for every end-to-end test: real Tomcat, real filter chain, real Postgres. Every subclass inherits the
 * SAME configuration, so Spring's test-context cache gives them one context and one Postgres container.
 * Nothing is rolled back over HTTP, so helpers always use unique names.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.auth.admin-password=" + ApiTestSupport.ADMIN_PASSWORD)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final String ADMIN_PASSWORD = "test-admin-pass";
    protected static final String CUSTOMER_PASSWORD = "customer-pass";

    /** A registered, logged-in customer. */
    protected record Customer(long id, String username, String accessToken) {
    }

    @Autowired
    protected RestTestClient client;

    protected static Consumer<HttpHeaders> bearer(String accessToken) {
        return headers -> headers.setBearerAuth(accessToken);
    }

    /** The admin account was created at startup by UserConfig's bootstrap, from app.auth.admin-password. */
    protected String adminToken() {
        return login("admin", ADMIN_PASSWORD).accessToken();
    }

    protected Customer newCustomer() {
        String username = "customer-" + UUID.randomUUID().toString().substring(0, 8);
        UserResponse user = client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest(username, CUSTOMER_PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        return new Customer(user.id(), username, login(username, CUSTOMER_PASSWORD).accessToken());
    }

    protected TokenResponse login(String username, String password) {
        return client.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest(username, password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TokenResponse.class).returnResult().getResponseBody();
    }

    protected long createCategory(String adminToken) {
        return client.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON).headers(bearer(adminToken))
                .body(new CategoryRequest("Category " + UUID.randomUUID()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody().id();
    }

    protected ProductResponse createProduct(String adminToken, long categoryId, String price, int stock) {
        return client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).headers(bearer(adminToken))
                .body(new ProductRequest(uniqueSku(), "Product", null, new BigDecimal(price), stock, categoryId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
    }

    protected static String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
