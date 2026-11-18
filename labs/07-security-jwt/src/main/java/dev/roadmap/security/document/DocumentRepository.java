package dev.roadmap.security.document;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

// ponytail: in-memory store keeps the lab about security, not persistence. Note 06 covers the JPA version.
@Repository
public class DocumentRepository {

    private final Map<Long, Document> documents = new ConcurrentHashMap<>(Map.of(
            1L, new Document(1, "alice", "Alice's roadmap"),
            2L, new Document(2, "bob", "Bob's notes"),
            3L, new Document(3, "admin", "Ops runbook")));

    public Optional<Document> findById(long id) {
        return Optional.ofNullable(documents.get(id));
    }

    public Document save(Document document) {
        documents.put(document.id(), document);
        return document;
    }

    public void deleteById(long id) {
        documents.remove(id);
    }
}
