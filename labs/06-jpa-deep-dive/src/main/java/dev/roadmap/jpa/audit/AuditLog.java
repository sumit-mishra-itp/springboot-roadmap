package dev.roadmap.jpa.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * IDENTITY id on purpose: the database assigns the id during INSERT, so Hibernate must run the INSERT
 * immediately on persist() to learn it. That rules out JDBC batching for this entity.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
        // for JPA
    }

    public AuditLog(String message) {
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }
}
