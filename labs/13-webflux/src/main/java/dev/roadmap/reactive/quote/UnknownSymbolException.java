package dev.roadmap.reactive.quote;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Domain exception. ErrorResponseException carries its own ProblemDetail, so the global handler can render it. */
public class UnknownSymbolException extends ErrorResponseException {

    public UnknownSymbolException(String symbol) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Unknown symbol " + symbol), null);
    }
}
