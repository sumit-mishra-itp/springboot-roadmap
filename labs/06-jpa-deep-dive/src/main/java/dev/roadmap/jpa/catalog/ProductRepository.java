package dev.roadmap.jpa.catalog;

import java.math.BigDecimal;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Row lock (Hibernate 7.4 on Postgres emits FOR NO KEY UPDATE): other writers block until this transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findLockedById(Long id);

    /** Same lock, but a timeout of 0 means NOWAIT: fail immediately instead of queueing. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    Optional<Product> findLockedNoWaitById(Long id);

    /** Bulk JPQL UPDATE: goes straight to the database, skips the persistence context, dirty checking and @Version. */
    @Modifying
    @Query("update Product p set p.price = p.price * :factor")
    int applyPriceFactor(BigDecimal factor);

    /** Same statement, but flushes pending changes first and clears the persistence context afterwards. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.price = p.price * :factor")
    int applyPriceFactorAndClear(BigDecimal factor);

    /** Native SQL is not touched by @SoftDelete, so this also counts soft-deleted rows. */
    @Query(value = "select count(*) from products", nativeQuery = true)
    long countIncludingSoftDeleted();
}
