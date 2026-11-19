package dev.roadmap.shop.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Entity name "CustomerOrder" as a precaution: ORDER is a JPQL keyword, and derived queries would otherwise generate
 * "from Order o". (Whether Hibernate 7 would accept the bare name was not tested.)
 * The customer is a plain id column, not a @ManyToOne: nothing here needs the user row, and the id comes from the JWT.
 */
@Entity(name = "CustomerOrder")
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private long customerId;

    @Column(nullable = false)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // @OneToMany is LAZY by default. Lines are saved with the order (cascade) and fetched on purpose by @EntityGraph.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    protected Order() {
        // for JPA
    }

    public Order(long customerId, Instant createdAt) {
        this.customerId = customerId;
        this.createdAt = createdAt;
    }

    /** Keeps both sides of the association and the total in sync. unitPrice is a snapshot: later price changes don't touch it. */
    public void addLine(long productId, int quantity, BigDecimal unitPrice) {
        lines.add(new OrderLine(this, productId, quantity, unitPrice));
        total = total.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Order other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Order.class.hashCode();
    }
}
