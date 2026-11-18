package dev.roadmap.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import dev.roadmap.jpa.audit.AuditLog;
import dev.roadmap.jpa.catalog.Category;
import dev.roadmap.jpa.catalog.Tag;
import dev.roadmap.jpa.customer.Customer;
import dev.roadmap.jpa.customer.CustomerRepository;

/** The persistence context (first-level cache), dirty checking, flush timing and id generation. */
@JpaSliceTest
class PersistenceContextTest {

    @Autowired TestEntityManager tem;
    @Autowired CustomerRepository customers;

    private EntityManager em;
    private Statistics stats;

    @BeforeEach
    void setUp() {
        em = tem.getEntityManager();
        stats = TestData.statistics(em);
    }

    @Test
    void sameIdInOneTransaction_returnsTheSameInstance_andHitsTheDatabaseOnce() {
        Long id = persistAndClear(new Customer("Ada", "ada@example.com"));
        stats.clear();

        Customer first = em.find(Customer.class, id);
        Customer second = customers.findById(id).orElseThrow();

        assertThat(second).isSameAs(first);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void jpqlQuery_alwaysHitsTheDatabase_butReturnsTheManagedInstance() {
        Long id = persistAndClear(new Customer("Ada", "ada@example.com"));
        Customer managed = em.find(Customer.class, id);
        stats.clear();

        Customer viaQuery = em.createQuery("select c from Customer c where c.id = :id", Customer.class)
                .setParameter("id", id)
                .getSingleResult();

        assertThat(viaQuery).isSameAs(managed);                    // identity map: one object per row per context
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1); // but a query is not a cache lookup
    }

    @Test
    void dirtyChecking_writesChangesAtFlush_withoutCallingSave() {
        Long id = persistAndClear(new Customer("Ada", "ada@example.com"));
        stats.clear();

        em.find(Customer.class, id).rename("Ada Lovelace");       // no save()
        assertThat(stats.getEntityUpdateCount()).isZero();         // nothing written yet
        em.flush();                                                // snapshot comparison happens here

        assertThat(stats.getEntityUpdateCount()).isEqualTo(1);
        em.clear();
        assertThat(em.find(Customer.class, id).getName()).isEqualTo("Ada Lovelace");
    }

    @Test
    void sequenceId_isAssignedOnPersist_butTheInsertWaitsForFlush() {
        stats.clear();
        var customer = new Customer("Grace", "grace@example.com");
        em.persist(customer);

        assertThat(customer.getId()).isNotNull();                  // from the pooled sequence
        assertThat(stats.getEntityInsertCount()).isZero();         // INSERT is queued in the action queue
    }

    @Test
    void identityId_forcesAnImmediateInsertOnPersist() {
        stats.clear();
        var log = new AuditLog("hello");
        em.persist(log);

        assertThat(log.getId()).isNotNull();
        assertThat(stats.getEntityInsertCount()).isEqualTo(1);     // the DB had to generate the id, so INSERT ran now
    }

    @Test
    void flushModeAuto_flushesBeforeAQuery_flushModeCommit_doesNot() {
        long before = countCustomers(FlushModeType.AUTO);
        em.persist(new Customer("Linus", "linus@example.com"));   // pending, not yet in the DB

        assertThat(countCustomers(FlushModeType.COMMIT)).isEqualTo(before);   // query sees stale DB state
        assertThat(countCustomers(FlushModeType.AUTO)).isEqualTo(before + 1); // Hibernate flushed first
    }

    @Test
    void nativeQuery_inAutoMode_flushesPendingChangesFirst() {
        long before = countCustomersNatively();
        em.persist(new Customer("Linus", "linus@example.com"));   // pending

        assertThat(countCustomersNatively()).isEqualTo(before + 1); // Hibernate cannot parse SQL, so it flushes
    }

    @Test
    void flushRunsInsertsBeforeDeletes_soReplacingAUniqueKeyFails() {
        var old = new Category("Books");
        em.persist(old);
        em.flush();

        em.remove(old);
        em.persist(new Category("Books"));                         // same unique name

        // ActionQueue order: INSERT first, DELETE last, so the INSERT hits the unique constraint.
        // The fix is an explicit em.flush() right after em.remove().
        assertThatThrownBy(em::flush)
                .rootCause()
                .hasMessageContaining("duplicate key");
    }

    @Test
    void sequenceIds_areJdbcBatched_identityIds_areNot() {
        stats.clear();
        for (int i = 0; i < 40; i++) {
            em.persist(new Customer("C" + i, "c" + i + "@example.com"));
        }
        em.flush();
        long sequenceStatements = stats.getPrepareStatementCount();

        stats.clear();
        for (int i = 0; i < 40; i++) {
            em.persist(new AuditLog("log " + i));
        }
        em.flush();
        long identityStatements = stats.getPrepareStatementCount();

        // SEQUENCE: nextval once or twice (pooled, 50 ids per call) + 2 batches of 20 INSERTs.
        assertThat(sequenceStatements).isLessThanOrEqualTo(4);
        // IDENTITY: every persist() executed its own INSERT ... RETURNING id.
        assertThat(identityStatements).isEqualTo(40);
    }

    @Test
    void hashCodeFromGeneratedId_losesTheEntityInAHashSet() {
        var naive = new Tag("java");
        var naiveSet = new HashSet<Tag>();
        naiveSet.add(naive);                                       // stored under hash(null)
        em.persist(naive);                                         // id assigned, so the hash changes

        assertThat(naiveSet.contains(naive)).isFalse();            // same object, "not in the set"

        var safe = new Category("Books");
        var safeSet = new HashSet<Category>();
        safeSet.add(safe);
        em.persist(safe);

        assertThat(safeSet.contains(safe)).isTrue();               // constant hashCode survives persist()
    }

    private Long persistAndClear(Customer customer) {
        em.persist(customer);
        em.flush();
        em.clear();
        return customer.getId();
    }

    private long countCustomersNatively() {
        return ((Number) em.createNativeQuery("select count(*) from customers").getSingleResult()).longValue();
    }

    private long countCustomers(FlushModeType flushMode) {
        return em.createQuery("select count(c) from Customer c", Long.class)
                .setFlushMode(flushMode)
                .getSingleResult();
    }
}
