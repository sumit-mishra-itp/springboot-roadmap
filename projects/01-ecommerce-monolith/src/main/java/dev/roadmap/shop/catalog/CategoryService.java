package dev.roadmap.shop.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.roadmap.shop.common.NotFoundException;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> list(Pageable pageable) {
        return categories.findAll(pageable).map(CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(long id) {
        return CategoryResponse.from(load(id));
    }

    /** A duplicate name is caught by the UNIQUE constraint (no check-then-insert race) and mapped to 409. */
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        return CategoryResponse.from(categories.save(new Category(request.name())));
    }

    @Transactional
    public CategoryResponse rename(long id, CategoryRequest request) {
        Category category = load(id);
        category.rename(request.name());
        return CategoryResponse.from(category);
    }

    /** Idempotent: deleting a missing id is a no-op. A category that still has products fails the FK, giving 409. */
    @Transactional
    public void delete(long id) {
        categories.deleteById(id);
    }

    private Category load(long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }
}
