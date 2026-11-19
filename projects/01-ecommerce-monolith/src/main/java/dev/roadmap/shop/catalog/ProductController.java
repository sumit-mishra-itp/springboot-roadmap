package dev.roadmap.shop.catalog;

import java.net.URI;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import dev.roadmap.shop.common.PageResponse;

/** HTTP concerns only. No try/catch: exceptions go to ApiExceptionHandler. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** ?categoryId=1&page=0&size=20&sort=price,desc. An unknown sort property is a 400 ProblemDetail. */
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) @Nullable Long categoryId,
                                              @ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return PageResponse.from(service.list(categoryId, pageable));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request, UriComponentsBuilder uri) {
        ProductResponse created = service.create(request);
        URI location = uri.path("/api/products/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse replace(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        return service.replace(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
