package dev.roadmap.shop.catalog;

public record CategoryResponse(long id, String name) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
