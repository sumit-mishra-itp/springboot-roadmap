package dev.roadmap.security.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP only. Every authorization decision lives in the filter chain (URLs) or DocumentService (domain rules). */
@RestController
public class DocumentController {

    public record RenameRequest(@NotBlank String title) {
    }

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/documents/{id}")
    public Document get(@PathVariable long id) {
        return documentService.findById(id);
    }

    @PutMapping("/api/documents/{id}")
    public Document rename(@PathVariable long id, @Valid @RequestBody RenameRequest request) {
        return documentService.rename(id, request.title());
    }

    @DeleteMapping("/api/admin/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        documentService.delete(id);
    }
}
