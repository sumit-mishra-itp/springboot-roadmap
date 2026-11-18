package dev.roadmap.jpa.catalog;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * DELIBERATELY BROKEN equals/hashCode, kept only to demonstrate the pitfall.
 * The hash is derived from a generated id, so it changes when persist() assigns the id,
 * and a HashSet that held the transient instance can no longer find it. Compare with {@link Category}.
 */
@Entity
@Table(name = "tags")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tags_seq")
    @SequenceGenerator(name = "tags_seq", sequenceName = "tags_seq", allocationSize = 50)
    private Long id;

    private String name;

    protected Tag() {
        // for JPA
    }

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Tag other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
