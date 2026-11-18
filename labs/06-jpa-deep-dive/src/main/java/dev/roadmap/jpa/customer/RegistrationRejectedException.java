package dev.roadmap.jpa.customer;

/** A checked exception: by default it does NOT roll back a Spring transaction. */
public class RegistrationRejectedException extends Exception {

    public RegistrationRejectedException(String email) {
        super("Registration rejected for " + email);
    }
}
