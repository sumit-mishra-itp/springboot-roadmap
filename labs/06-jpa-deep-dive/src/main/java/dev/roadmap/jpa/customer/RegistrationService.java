package dev.roadmap.jpa.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.roadmap.jpa.audit.AuditService;

/** Each method isolates one transaction rule so a test can prove it. */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final CustomerRepository customers;
    private final AuditService audit;

    public RegistrationService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    // --- Propagation -----------------------------------------------------------------------------

    @Transactional
    public void registerThenFail(String name, String email, boolean auditInNewTransaction) {
        customers.save(new Customer(name, email));
        if (auditInNewTransaction) {
            audit.recordInNewTransaction("registration attempt " + email);
        } else {
            audit.recordInCallerTransaction("registration attempt " + email);
        }
        throw new IllegalStateException("payment gateway down");
    }

    /** Catching the inner exception does not help: the shared transaction is already rollback-only. */
    @Transactional
    public void registerSwallowingInnerFailure(String name, String email) {
        customers.save(new Customer(name, email));
        try {
            audit.recordThenFail("registration " + email);
        } catch (IllegalStateException e) {
            log.warn("Ignoring audit failure: {}", e.getMessage());
        }
    }

    // --- Rollback rules --------------------------------------------------------------------------

    /** Default rule: checked exceptions COMMIT (an EJB-era convention: a checked exception is a business outcome). */
    @Transactional
    public void registerThenThrowChecked(String name, String email) throws RegistrationRejectedException {
        customers.save(new Customer(name, email));
        throw new RegistrationRejectedException(email);
    }

    @Transactional(rollbackFor = RegistrationRejectedException.class)
    public void registerThenThrowCheckedWithRollbackFor(String name, String email) throws RegistrationRejectedException {
        customers.save(new Customer(name, email));
        throw new RegistrationRejectedException(email);
    }

    @Transactional
    public void registerThenThrowUnchecked(String name, String email) {
        customers.save(new Customer(name, email));
        throw new IllegalArgumentException("blocked email domain: " + email);
    }

    // --- Dirty checking and readOnly -------------------------------------------------------------

    /** No save() call: the entity is managed, so the change is detected and flushed at commit. */
    @Transactional
    public void rename(Long customerId, String newName) {
        customers.findById(customerId).orElseThrow().rename(newName);
    }

    /** readOnly = true: Hibernate session is read-only and flush mode MANUAL, so the change is never written. */
    @Transactional(readOnly = true)
    public void renameInReadOnlyTransaction(Long customerId, String newName) {
        customers.findById(customerId).orElseThrow().rename(newName);
    }

    // --- Proxy boundaries ------------------------------------------------------------------------

    /** Not annotated. It calls an annotated method on {@code this}, which bypasses the proxy. */
    public boolean selfInvocationRunsInTransaction() {
        return transactionalViaThis();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transactionalViaThis() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    /** Package-private: Framework 6.0+ CGLIB proxies intercept it (only private and final methods are skipped). */
    @Transactional
    boolean packagePrivateRunsInTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive();
    }
}
