package dev.roadmap.jpa.customer;

/** DTO projection: an immutable record, safe to hand to any layer or serialize. */
public record CustomerView(String name, String email) {
}
