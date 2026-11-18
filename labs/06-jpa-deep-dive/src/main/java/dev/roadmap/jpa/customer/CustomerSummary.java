package dev.roadmap.jpa.customer;

/** Closed interface projection: every getter maps to an entity property, so Spring can narrow the SELECT. */
public interface CustomerSummary {

    String getName();

    String getEmail();
}
