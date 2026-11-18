package dev.roadmap.jpa.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.BatchSize;

import dev.roadmap.jpa.catalog.Product;
import dev.roadmap.jpa.customer.Customer;

/** Named PurchaseOrder because ORDER is an SQL keyword. Order N-N Product is modelled through OrderLine. */
@Entity
@Table(name = "orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
    private Long id;

    // Owning side: this field's @JoinColumn is the FK that Hibernate writes.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    // Lines live and die with their order: cascade ALL + orphanRemoval (removing from the list = DELETE).
    // @BatchSize: touching the lines of one order loads the lines of up to 5 orders in one IN/ANY query.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 5)
    private List<OrderLine> lines = new ArrayList<>();

    @Column(nullable = false)
    private Instant placedAt;

    protected PurchaseOrder() {
        // for JPA
    }

    public PurchaseOrder(Customer customer) {
        this.customer = customer;
        this.placedAt = Instant.now();
        customer.addOrder(this);
    }

    public void addLine(Product product, int quantity) {
        lines.add(new OrderLine(this, product, quantity, product.getPrice()));
    }

    public void removeLine(OrderLine line) {
        lines.remove(line);
    }

    public BigDecimal total() {
        return lines.stream().map(OrderLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PurchaseOrder other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return PurchaseOrder.class.hashCode();
    }
}
