package dev.roadmap.cache.catalog;

/** The cached value. A plain record: the JSON serializer stores it with an "@class" hint so it comes back typed. */
public record Product(long id, String sku, String name, String category, long priceCents) {

    public Product withPrice(long newPriceCents) {
        return new Product(id, sku, name, category, newPriceCents);
    }

    public Product withName(String newName) {
        return new Product(id, sku, newName, category, priceCents);
    }
}
