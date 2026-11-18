package dev.roadmap.security.document;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Referenced from SpEL as @documentGuard. Keeps authorization rules in testable Java instead of long
 * expression strings. A missing document returns false, so callers get 403 and can't probe which ids exist.
 */
@Component("documentGuard")
public class DocumentGuard {

    private final DocumentRepository repository;

    public DocumentGuard(DocumentRepository repository) {
        this.repository = repository;
    }

    public boolean isOwner(long documentId, Authentication authentication) {
        return repository.findById(documentId)
                .map(document -> document.owner().equals(authentication.getName()))
                .orElse(false);
    }
}
