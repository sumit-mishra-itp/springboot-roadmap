package dev.roadmap.security.document;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Method security: the bean is proxied, and AuthorizationManagerBeforeMethodInterceptor /
 * AuthorizationManagerAfterMethodInterceptor evaluate the SpEL around each call.
 * Self-invocation (this.findById) skips the proxy and therefore skips these checks.
 */
@Service
public class DocumentService {

    private final DocumentRepository repository;

    public DocumentService(DocumentRepository repository) {
        this.repository = repository;
    }

    /** Runs the method FIRST, then checks the returned domain object. Use only for side-effect-free reads. */
    @PostAuthorize("returnObject.owner == authentication.name or hasRole('ADMIN')")
    public Document findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document " + id + " not found"));
    }

    /** Checked BEFORE the write happens, against the stored document, via a bean reference. */
    @PreAuthorize("@documentGuard.isOwner(#id, authentication)")
    public Document rename(long id, String title) {
        Document current = findById(id); // self-call: @PostAuthorize is NOT re-applied here
        return repository.save(new Document(current.id(), current.owner(), title));
    }

    /** Defense in depth: the URL rule already demands ADMIN for /api/admin/**, and the service says so too. */
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(long id) {
        repository.deleteById(id);
    }
}
