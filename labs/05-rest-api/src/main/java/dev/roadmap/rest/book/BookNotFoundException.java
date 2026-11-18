package dev.roadmap.rest.book;

/** Plain domain exception. The service stays HTTP-free, and ApiExceptionHandler maps this to a 404 ProblemDetail. */
public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(long id) {
        super("Book " + id + " does not exist");
    }
}
