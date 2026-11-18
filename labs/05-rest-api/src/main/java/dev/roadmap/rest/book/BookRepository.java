package dev.roadmap.rest.book;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongFunction;
import java.util.function.UnaryOperator;

import org.springframework.stereotype.Repository;

/**
 * In-memory store. It uses a ConcurrentSkipListMap rather than a ConcurrentHashMap because it is thread-safe AND
 * sorted by id, which offset and keyset pagination both need.
 */
@Repository
public class BookRepository {

    private final AtomicLong ids = new AtomicLong();
    private final ConcurrentSkipListMap<Long, Book> books = new ConcurrentSkipListMap<>();

    public Book insert(LongFunction<Book> factory) {
        Book book = factory.apply(ids.incrementAndGet());
        books.put(book.id(), book);
        return book;
    }

    public Optional<Book> findById(long id) {
        return Optional.ofNullable(books.get(id));
    }

    /** Atomic read-modify-write: the version check and the write cannot interleave with another update. */
    public Optional<Book> update(long id, UnaryOperator<Book> change) {
        return Optional.ofNullable(books.computeIfPresent(id, (key, current) -> change.apply(current)));
    }

    public void delete(long id) {
        books.remove(id);
    }

    public long count() {
        return books.size();
    }

    /** OFFSET/LIMIT: cost grows with the offset, and rows shift when data changes between page requests. */
    public List<Book> findPage(int page, int size) {
        return books.values().stream().skip((long) page * size).limit(size).toList();
    }

    /** Keyset: the in-memory twin of "WHERE id > :after ORDER BY id LIMIT :limit". Cost does not grow with depth. */
    public List<Book> findAfter(long afterId, int limit) {
        return books.tailMap(afterId, false).values().stream().limit(limit).toList();
    }
}
