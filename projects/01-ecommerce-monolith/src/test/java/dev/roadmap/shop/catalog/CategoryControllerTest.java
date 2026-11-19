package dev.roadmap.shop.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CategoryService service;

    @Test
    void create_returns201WithLocation() {
        given(service.create(any())).willReturn(new CategoryResponse(3, "Books"));

        assertThat(mvc.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Books"}"""))
                .hasStatus(HttpStatus.CREATED)
                .headers().hasValue("Location", "http://localhost/api/categories/3");
    }

    @Test
    void create_withBlankName_returns400Problem() {
        assertThat(mvc.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":""}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": 400, "errors": [{"field": "name", "message": "must not be blank"}]}""");
    }

    @Test
    void create_withMalformedJson_returns400Problem() {
        // Not one of our handlers: ResponseEntityExceptionHandler's default still answers with application/problem+json
        assertThat(mvc.post().uri("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void delete_returns204() {
        assertThat(mvc.delete().uri("/api/categories/3")).hasStatus(HttpStatus.NO_CONTENT);
        verify(service).delete(3);
    }
}
