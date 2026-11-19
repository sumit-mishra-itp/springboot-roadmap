package dev.roadmap.shop.catalog;

import org.jspecify.annotations.Nullable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.roadmap.shop.common.NotFoundException;

/**
 * Returns DTOs, never entities. With open-in-view=false the session closes when the transaction ends, so the
 * entity-to-DTO mapping (which reads the LAZY category) has to happen in here.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(@Nullable Long categoryId, Pageable pageable) {
        Page<Product> page = categoryId == null
                ? products.findAll(pageable)
                : products.findByCategoryId(categoryId, pageable);
        return page.map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return ProductResponse.from(load(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(request.sku(), request.name(), request.description(), request.price(),
                request.stock(), category(request.categoryId()));
        return ProductResponse.from(products.save(product));
    }

    /** The UPDATE carries "AND version = ?", so a concurrent stock change makes this fail with 409 instead of overwriting it. */
    @Transactional
    public ProductResponse replace(long id, ProductRequest request) {
        Product product = load(id);
        product.replaceWith(request.sku(), request.name(), request.description(), request.price(),
                request.stock(), category(request.categoryId()));
        // Flush now so the bumped version (and any unique/version conflict) is visible before we build the response.
        products.flush();
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(long id) {
        products.deleteById(id);
    }

    private Product load(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private Category category(long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }
}
