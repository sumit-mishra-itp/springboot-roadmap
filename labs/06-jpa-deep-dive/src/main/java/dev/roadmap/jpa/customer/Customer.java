package dev.roadmap.jpa.customer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import dev.roadmap.jpa.order.PurchaseOrder;

@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class) // fills @CreatedDate/@LastModifiedDate on @PrePersist/@PreUpdate
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customers_seq")
    @SequenceGenerator(name = "customers_seq", sequenceName = "customers_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Inverse side: mappedBy names the field that OWNS the foreign key (PurchaseOrder.customer).
    // Changes made only to this list are ignored when writing SQL. @OneToMany is LAZY by default.
    @OneToMany(mappedBy = "customer")
    private List<PurchaseOrder> orders = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    protected Customer() {
        // for JPA
    }

    public Customer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    /** Keeps both sides of the bidirectional association in sync, in memory. */
    public void addOrder(PurchaseOrder order) {
        orders.add(order);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public List<PurchaseOrder> getOrders() {
        return orders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Customer other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Customer.class.hashCode();
    }
}
