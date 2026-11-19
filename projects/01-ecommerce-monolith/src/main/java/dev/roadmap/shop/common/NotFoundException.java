package dev.roadmap.shop.common;

/** One exception for every "no such resource" case. ApiExceptionHandler turns it into a 404 ProblemDetail. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, long id) {
        super(resource + " " + id + " not found");
    }
}
