package dev.roadmap.rest.book;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.stereotype.Service;

import dev.roadmap.rest.idempotency.IdempotencyStore;
import dev.roadmap.rest.idempotency.IdempotencyStore.Outcome;

@Service
public class BookService {

    private final BookRepository repository;
    private final IdempotencyStore idempotency;

    public BookService(BookRepository repository, IdempotencyStore idempotency) {
        this.repository = repository;
        this.idempotency = idempotency;
    }

    /** With a key, a retried POST returns the first result instead of creating a duplicate. */
    public Outcome<Book> create(BookRequest request, @Nullable String idempotencyKey) {
        if (idempotencyKey == null) {
            return new Outcome<>(insert(request), false);
        }
        return idempotency.execute(idempotencyKey, request, () -> insert(request));
    }

    public Book find(long id) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    /** PUT = full replacement. expectedVersion comes from If-Match, and null means "unconditional". */
    public Book replace(long id, BookRequest request, @Nullable Long expectedVersion) {
        return repository.update(id, current -> {
            if (expectedVersion != null && expectedVersion != current.version()) {
                throw new StaleVersionException(id, expectedVersion, current.version());
            }
            return request.toBook(id, current.version() + 1);
        }).orElseThrow(() -> new BookNotFoundException(id));
    }

    public void delete(long id) {
        repository.delete(id);
    }

    public PageResponse<Book> page(int page, int size) {
        long total = repository.count();
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(repository.findPage(page, size), page, size, total, totalPages);
    }

    /** Fetches one extra row to learn whether a next page exists, so no COUNT query is needed. */
    public CursorPage<Book> feed(long afterId, int limit) {
        List<Book> rows = repository.findAfter(afterId, limit + 1);
        if (rows.size() <= limit) {
            return new CursorPage<>(rows, null);
        }
        List<Book> page = rows.subList(0, limit);
        return new CursorPage<>(page, Cursor.encode(page.getLast().id()));
    }

    private Book insert(BookRequest request) {
        return repository.insert(id -> request.toBook(id, 1));
    }
}
