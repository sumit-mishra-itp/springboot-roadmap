package dev.roadmap.jpa.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;

import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;

import dev.roadmap.jpa.IntegrationTest;
import dev.roadmap.jpa.TestData;
import dev.roadmap.jpa.audit.AuditLogRepository;

/**
 * Real commits and rollbacks through the @Transactional proxy. Lives in the customer package only so it can
 * call the package-private @Transactional method.
 */
@IntegrationTest
class TransactionBehaviourTest {

    @Autowired RegistrationService registration;
    @Autowired CustomerRepository customers;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        TestData.truncateAll(jdbc);
    }

    // --- Propagation -----------------------------------------------------------------------------

    @Test
    void requiresNew_innerCommitSurvivesOuterRollback() {
        assertThatThrownBy(() -> registration.registerThenFail("Ada", "ada@example.com", true))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customers.count()).isZero();     // outer transaction rolled back
        assertThat(auditLogs.count()).isEqualTo(1); // inner REQUIRES_NEW had already committed
    }

    @Test
    void required_innerWorkRollsBackWithOuter() {
        assertThatThrownBy(() -> registration.registerThenFail("Ada", "ada@example.com", false))
                .isInstanceOf(IllegalStateException.class);

        assertThat(customers.count()).isZero();
        assertThat(auditLogs.count()).isZero();     // same physical transaction, same fate
    }

    @Test
    void catchingAnInnerRequiredFailure_stillRollsBack_withUnexpectedRollbackException() {
        assertThatThrownBy(() -> registration.registerSwallowingInnerFailure("Ada", "ada@example.com"))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        assertThat(customers.count()).isZero();
    }

    @Test
    void mandatoryAndNever_enforceTheirPreconditions() {
        assertThatThrownBy(() -> template(TransactionDefinition.PROPAGATION_MANDATORY).executeWithoutResult(s -> { }))
                .isInstanceOf(IllegalTransactionStateException.class);       // MANDATORY with no transaction

        assertThatThrownBy(() -> template(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(outer ->
                template(TransactionDefinition.PROPAGATION_NEVER).executeWithoutResult(inner -> { })))
                .isInstanceOf(IllegalTransactionStateException.class);       // NEVER inside a transaction
    }

    @Test
    void nested_isNotSupportedByJpaTransactionManager_byDefault() {
        assertThatThrownBy(() -> template(TransactionDefinition.PROPAGATION_REQUIRED).executeWithoutResult(outer ->
                template(TransactionDefinition.PROPAGATION_NESTED).executeWithoutResult(inner -> { })))
                .isInstanceOf(NestedTransactionNotSupportedException.class); // nestedTransactionAllowed = false
    }

    // --- Rollback rules --------------------------------------------------------------------------

    @Test
    void checkedException_commitsByDefault() {
        assertThatThrownBy(() -> registration.registerThenThrowChecked("Ada", "ada@example.com"))
                .isInstanceOf(RegistrationRejectedException.class);

        assertThat(customers.count()).isEqualTo(1);
    }

    @Test
    void checkedException_withRollbackFor_rollsBack() {
        assertThatThrownBy(() -> registration.registerThenThrowCheckedWithRollbackFor("Ada", "ada@example.com"))
                .isInstanceOf(RegistrationRejectedException.class);

        assertThat(customers.count()).isZero();
    }

    @Test
    void runtimeException_rollsBack() {
        assertThatThrownBy(() -> registration.registerThenThrowUnchecked("Ada", "ada@example.com"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(customers.count()).isZero();
    }

    // --- Dirty checking and readOnly -------------------------------------------------------------

    @Test
    void managedEntityChanges_areSavedWithoutCallingSave() {
        Long id = customers.save(new Customer("Ada", "ada@example.com")).getId();

        registration.rename(id, "Ada Lovelace");

        assertThat(customers.findById(id).orElseThrow().getName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void readOnlyTransaction_neverFlushesChanges() {
        Long id = customers.save(new Customer("Ada", "ada@example.com")).getId();

        registration.renameInReadOnlyTransaction(id, "Ada Lovelace");

        assertThat(customers.findById(id).orElseThrow().getName()).isEqualTo("Ada");
    }

    @Test
    void readOnlyTransaction_isAlsoReadOnlyInPostgres() {
        var readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        // Spring calls Connection.setReadOnly(true); PgJDBC turns that into a READ ONLY Postgres transaction.
        assertThatThrownBy(() -> readOnly.executeWithoutResult(status ->
                customers.saveAndFlush(new Customer("Ada", "ada@example.com"))))
                .rootCause()
                .hasMessageContaining("read-only transaction");
    }

    // --- Proxy boundaries ------------------------------------------------------------------------

    @Test
    void selfInvocation_bypassesTheProxy() {
        assertThat(registration.transactionalViaThis()).isTrue();             // called through the proxy
        assertThat(registration.selfInvocationRunsInTransaction()).isFalse(); // same method, called via this
    }

    @Test
    void packagePrivateTransactionalMethod_isIntercepted() {
        assertThat(registration.packagePrivateRunsInTransaction()).isTrue();
    }

    // --- Lazy loading ----------------------------------------------------------------------------

    @Test
    void lazyCollection_outsideATransaction_throwsLazyInitializationException() {
        Long id = new TransactionTemplate(transactionManager).execute(status ->
                TestData.customersWithOrders(em, 1, 2).getFirst().getId());

        Customer detached = customers.findById(id).orElseThrow(); // repository tx ended, open-in-view is off

        assertThatThrownBy(() -> detached.getOrders().size())
                .isInstanceOf(LazyInitializationException.class);

        Customer fetched = customers.findWithOrdersById(id).orElseThrow(); // the fix: fetch what you need
        assertThat(fetched.getOrders()).hasSize(2);
    }

    private TransactionTemplate template(int propagation) {
        var template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
    }
}
