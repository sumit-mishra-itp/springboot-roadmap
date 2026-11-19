package dev.roadmap.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import dev.roadmap.shop.catalog.CategoryRequest;
import dev.roadmap.shop.catalog.CategoryResponse;
import dev.roadmap.shop.catalog.ProductRequest;
import dev.roadmap.shop.catalog.ProductResponse;
import dev.roadmap.shop.common.ApiExceptionHandler;

/**
 * End to end over real HTTP: Tomcat, MVC, the advice, the services' transactions, Hibernate and Flyway on a real
 * Postgres. This is where the failures a mocked slice cannot see show up: lazy loading after the transaction,
 * constraint violations raised at COMMIT, and Spring Data rejecting an unknown sort property.
 * Nothing is rolled back here, so every test uses its own unique names.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
class CatalogApiTest {

    @Autowired
    RestTestClient client;

    @Test
    void catalogRoundTrip_createListFilterSortUpdateDelete() {
        long categoryId = createCategory();
        createProduct(categoryId, "29.90");
        ProductResponse expensive = createProduct(categoryId, "99.00");
        createProduct(createCategory(), "10.00"); // another category, filtered out below

        client.get().uri("/api/products?categoryId={c}&sort=price,desc", categoryId).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].price").isEqualTo(99.00)
                .jsonPath("$.content[1].price").isEqualTo(29.90)
                .jsonPath("$.content[0].categoryName").isNotEmpty();

        ProductResponse updated = client.put().uri("/api/products/{id}", expensive.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ProductRequest(expensive.sku(), "Renamed", "now with a description", new BigDecimal("89.00"), 3, categoryId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
        assertThat(updated.version()).isEqualTo(expensive.version() + 1);
        assertThat(updated.stock()).isEqualTo(3);

        client.delete().uri("/api/products/{id}", expensive.id()).exchange().expectStatus().isNoContent();
        client.get().uri("/api/products/{id}", expensive.id()).exchange().expectStatus().isNotFound();
    }

    @Test
    void duplicateSku_returns409Problem() {
        long categoryId = createCategory();
        ProductResponse first = createProduct(categoryId, "5.00");

        // The INSERT only runs at commit (sequence ids are pre-allocated), after the service method returned.
        client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON)
                .body(new ProductRequest(first.sku(), "Copy", null, new BigDecimal("5.00"), 1, categoryId))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void deletingACategoryThatStillHasProducts_returns409Problem() {
        long categoryId = createCategory();
        createProduct(categoryId, "5.00");

        client.delete().uri("/api/categories/{id}", categoryId).exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void productInAMissingCategory_returns404Problem() {
        client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON)
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
    void openApiDocs_documentTheCatalogEndpoints() {
        client.get().uri("/v3/api-docs").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("ShopSphere API")
                .jsonPath("$.paths['/api/products'].get").exists()
                .jsonPath("$.paths['/api/products/{id}'].put").exists()
                .jsonPath("$.paths['/api/categories'].post").exists();
    }

    private long createCategory() {
        return client.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON)
                .body(new CategoryRequest("Category " + UUID.randomUUID()))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(CategoryResponse.class).returnResult().getResponseBody().id();
    }

    private ProductResponse createProduct(long categoryId, String price) {
        return client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON)
                .body(new ProductRequest(uniqueSku(), "Product", null, new BigDecimal(price), 10, categoryId))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
    }

    private static String uniqueSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
