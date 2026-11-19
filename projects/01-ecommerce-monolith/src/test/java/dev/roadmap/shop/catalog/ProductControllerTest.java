package dev.roadmap.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.roadmap.shop.auth.SecurityConfig;
import dev.roadmap.shop.common.ApiExceptionHandler;
import dev.roadmap.shop.common.NotFoundException;

/**
 * Web slice: controller + advice + Spring Data web support (Pageable binding) + the REAL SecurityConfig. The service is
 * a mock. Requests run as ADMIN unless a test says otherwise, and the last tests pin down the catalog URL rules.
 */
@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "ADMIN")
class ProductControllerTest {

    static final String KEYBOARD_JSON = """
            {"sku":"KB-001","name":"Mechanical keyboard","price":89.90,"stock":5,"categoryId":1}""";
    static final ProductResponse KEYBOARD =
            new ProductResponse(7, "KB-001", "Mechanical keyboard", null, new BigDecimal("89.90"), 5, 0, 1, "Peripherals");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ProductService service;

    @Test
    void create_returns201WithLocationAndBody() {
        given(service.create(any())).willReturn(KEYBOARD);

        MvcTestResult result = post(KEYBOARD_JSON);

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .headers().hasValue("Location", "http://localhost/api/products/7");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"id": 7, "sku": "KB-001", "price": 89.90, "stock": 5, "categoryName": "Peripherals"}""");
    }

    @Test
    void create_withInvalidBody_returns400ProblemWithEveryFieldError() {
        MvcTestResult result = post("""
                {"sku":"kb 1","name":" ","price":0}""");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "type": "https://api.shopsphere.dev/problems/validation-error",
                  "title": "Validation failed",
                  "status": 400,
                  "detail": "5 invalid value(s)",
                  "instance": "/api/products",
                  "errors": [
                    {"field": "categoryId", "message": "must not be null"},
                    {"field": "name",       "message": "must not be blank"},
                    {"field": "price",      "message": "must be greater than or equal to 0.01"},
                    {"field": "sku",        "message": "must be upper-case letters, digits or '-'"},
                    {"field": "stock",      "message": "must not be null"}
                  ]
                }""");
    }

    @Test
    void get_unknownId_returns404Problem() {
        given(service.get(99)).willThrow(new NotFoundException("Product", 99));

        assertThat(mvc.get().uri("/api/products/99"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().isLenientlyEqualTo("""
                        {"type": "%snot-found", "status": 404, "detail": "Product 99 not found", "instance": "/api/products/99"}"""
                        .formatted(ApiExceptionHandler.PROBLEM_BASE));
    }

    @Test
    void create_whenDatabaseRejectsDuplicate_returns409Problem() {
        given(service.create(any())).willThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThat(post(KEYBOARD_JSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "conflict");
    }

    @Test
    void replace_whenVersionCheckFails_returns409Problem() {
        given(service.replace(anyLong(), any())).willThrow(new ObjectOptimisticLockingFailureException(Product.class, 7L));

        assertThat(mvc.put().uri("/api/products/7").contentType(MediaType.APPLICATION_JSON).content(KEYBOARD_JSON))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "concurrent-update");
    }

    @Test
    void list_bindsFilterPageAndSort_andClampsSizeTo100() {
        given(service.list(any(), any())).willAnswer(call -> new PageImpl<>(List.of(KEYBOARD), call.getArgument(1), 101));

        MvcTestResult result = mvc.get().uri("/api/products?categoryId=1&page=1&size=500&sort=price,desc").exchange();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(eq(1L), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100); // spring.data.web.pageable.max-page-size
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by(Sort.Order.desc("price")));
        assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"page": 1, "size": 100, "totalElements": 101, "totalPages": 2, "content": [{"id": 7}]}""");
    }

    @Test
    void list_withoutParams_usesFirstPageOf20SortedById() {
        given(service.list(any(), any())).willReturn(Page.empty());

        assertThat(mvc.get().uri("/api/products")).hasStatusOk();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).list(isNull(), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20, Sort.by("id")));
    }

    @Test
    @WithAnonymousUser
    void list_anonymous_isPublic() {
        given(service.list(any(), any())).willReturn(Page.empty());

        assertThat(mvc.get().uri("/api/products")).hasStatusOk();
    }

    @Test
    @WithAnonymousUser
    void create_anonymous_returns401Problem() {
        assertThat(post(KEYBOARD_JSON))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "unauthorized");
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void create_asCustomer_returns403Problem() {
        assertThat(post(KEYBOARD_JSON))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.type").isEqualTo(ApiExceptionHandler.PROBLEM_BASE + "forbidden");
        verify(service, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void delete_asCustomer_returns403() {
        assertThat(mvc.delete().uri("/api/products/7")).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult post(String json) {
        return mvc.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }
}
