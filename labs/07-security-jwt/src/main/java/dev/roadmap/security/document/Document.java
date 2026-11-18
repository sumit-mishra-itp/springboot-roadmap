package dev.roadmap.security.document;

/** The domain object that method-security SpEL inspects (returnObject.owner). */
public record Document(long id, String owner, String title) {
}
