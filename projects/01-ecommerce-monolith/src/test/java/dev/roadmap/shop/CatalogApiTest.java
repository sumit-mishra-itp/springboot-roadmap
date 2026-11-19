package dev.roadmap.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import dev.roadmap.shop.catalog.CategoryRequest;
import dev.roadmap.shop.catalog.ProductRequest;
import dev.roadmap.shop.catalog.ProductResponse;
import dev.roadmap.shop.common.ApiExceptionHandler;

/**
 * End to end over real HTTP: Tomcat, the security filter chain, MVC, the advice, the services' transactions, Hibernate
 * and Flyway on a real Postgres. This is where the failures a mocked slice cannot see show up: lazy loading after the
 * transaction, constraint violations raised at COMMIT, and Spring Data rejecting an unknown sort property.
 * Writes use an ADMIN token, reads go out anonymously (the catalog is public).
 */
class CatalogApiTest extends ApiTestSupport {

    String admin;

    @BeforeEach
    void loginAsAdmin() {
        admin = adminToken();
    }

    @Test
    void catalogRoundTrip_createListFilterSortUpdateDelete() {
        long categoryId = createCategory(admin);
        createProduct(admin, categoryId, "29.90", 10);
        ProductResponse expensive = createProduct(admin, categoryId, "99.00", 10);
        createProduct(admin, createCategory(admin), "10.00", 10); // another category, filtered out below

        client.get().uri("/api/products?categoryId={c}&sort=price,desc", categoryId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].price").isEqualTo(99.00)
                .jsonPath("$.content[1].price").isEqualTo(29.90)
                .jsonPath("$.content[0].categoryName").isNotEmpty();

        ProductResponse updated = client.put().uri("/api/products/{id}", expensive.id())
                .contentType(MediaType.APPLICATION_JSON).headers(bearer(admin))
                .body(new ProductRequest(expensive.sku(), "Renamed", "now with a description", new BigDecimal("89.00"), 3, categoryId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
        assertThat(updated.version()).isEqualTo(expensive.version() + 1);
        assertThat(updated.stock()).isEqualTo(3);

        client.delete().uri("/api/products/{id}", expensive.id()).headers(bearer(admin)).exchange().expectStatus().isNoContent();
        client.get().uri("/api/products/{id}", expensive.id()).exchange().expectStatus().isNotFound();
    }

    @Test
    void duplicateSku_returns409Problem() {
        long categoryId = createCategory(admin);
        ProductResponse first = createProduct(admin, categoryId, "5.00", 10);

        // The INSERT only runs at commit (sequence ids are pre-allocated), after the service method returned.
        client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).headers(bearer(admin))
                .body(new ProductRequest(first.sku(), "Copy", null, new BigDecimal("5.00"), 1, categoryId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void deletingACategoryThatStillHasProducts_returns409Problem() {
        long categoryId = createCategory(admin);
        createProduct(admin, categoryId, "5.00", 10);

        client.delete().uri("/api/categories/{id}", categoryId).headers(bearer(admin)).exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void productInAMissingCategory_returns404Problem() {
        client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).headers(bearer(admin))
                .body(new ProductRequest(uniqueSku(), "Orphan", null, new BigDecimal("5.00"), 1, 999_999L))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.detail").isEqualTo("Category 999999 not found");
    }

    @Test
    void unknownSortProperty_returns400Problem() {
        client.get().uri("/api/products?sort=bogus,asc").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "invalid-sort")
                .jsonPath("$.detail").isEqualTo("Unknown sort property 'bogus'");
    }

    @Test
    void catalogWrite_withCustomerToken_returns403Problem() {
        Customer customer = newCustomer();

        client.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON).headers(bearer(customer.accessToken()))
                .body(new CategoryRequest("Not allowed"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "forbidden");
    }

    @Test
    void openApiDocs_arePublic_andDocumentTheApiWithABearerScheme() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("ShopSphere API")
                .jsonPath("$.paths['/api/products'].get").exists()
                .jsonPath("$.paths['/api/products/{id}'].put").exists()
                .jsonPath("$.paths['/api/categories'].post").exists()
                .jsonPath("$.paths['/api/orders'].post").exists()
                .jsonPath("$.paths['/api/orders'].get.parameters[?(@.name == 'page')]").exists()
                .jsonPath("$.paths['/api/orders'].get.parameters[?(@.name == 'caller')]").doesNotExist() // Authentication param hidden
                .jsonPath("$.paths['/api/auth/login'].post").exists()
                .jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");
    }

    @Test
    void swaggerUi_isPublic() {
        client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
    }
}
