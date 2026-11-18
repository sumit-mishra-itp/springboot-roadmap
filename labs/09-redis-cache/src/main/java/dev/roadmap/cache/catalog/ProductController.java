package dev.roadmap.cache.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/quote")
    public PriceQuote quote(@PathVariable long id, @RequestParam @Pattern(regexp = "[A-Z]{3}") String currency) {
        return service.quote(id, currency);
    }

    @PutMapping("/{id}/price")
    public Product updatePrice(@PathVariable long id, @Valid @RequestBody PriceUpdate request) {
        return service.updatePrice(id, request.priceCents());
    }

    public record PriceUpdate(@Positive long priceCents) {
    }
}
