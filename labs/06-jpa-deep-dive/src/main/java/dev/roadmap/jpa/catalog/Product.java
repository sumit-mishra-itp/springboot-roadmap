package dev.roadmap.jpa.catalog;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.SoftDelete;

/**
 * {@code @SoftDelete}: remove() becomes {@code UPDATE products SET deleted = true}, and every
 * HQL/Criteria/find() silently adds {@code deleted = false}. Native SQL is NOT filtered.
 */
@Entity
@Table(name = "products")
@SoftDelete
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "products_seq")
    @SequenceGenerator(name = "products_seq", sequenceName = "products_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, unique = true)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price;

    private int stock;

    // Hibernate adds "AND version = ?" to every UPDATE/DELETE and bumps it. 0 rows updated = someone else won.
    @Version
    private long version;

    // @ManyToOne is EAGER by default in JPA. Always say LAZY.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    protected Product() {
        // for JPA
    }

    public Product(String sku, String name, BigDecimal price, int stock, Category category) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.category = category;
    }

    public void decrementStock(int quantity) {
        if (stock < quantity) {
            throw new OutOfStockException(sku);
        }
        stock -= quantity;
    }

    public void changePrice(BigDecimal newPrice) {
        this.price = newPrice;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public long getVersion() {
        return version;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Product.class.hashCode();
    }
}
