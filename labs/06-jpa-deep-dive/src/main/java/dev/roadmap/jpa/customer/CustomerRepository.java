package dev.roadmap.jpa.customer;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // --- N+1 fixes -------------------------------------------------------------------------------

    /** Fetch join: one SQL with a LEFT JOIN. Hibernate 6+ de-duplicates the root entities, no DISTINCT needed. */
    @Query("select c from Customer c left join fetch c.orders")
    List<Customer> findAllWithOrdersFetchJoin();

    /** Entity graph: same SQL, but the fetch plan is declared next to the query instead of inside it. */
    @EntityGraph(attributePaths = "orders")
    @Query("select c from Customer c")
    List<Customer> findAllWithOrdersEntityGraph();

    @EntityGraph(attributePaths = "orders")
    Optional<Customer> findWithOrdersById(Long id);

    // --- Projections -----------------------------------------------------------------------------

    /** Interface projection: Spring returns a proxy backed by a tuple. Only name and email are selected. */
    List<CustomerSummary> findSummariesByNameStartingWithOrderByNameAsc(String prefix);

    /** Class-based (DTO) projection: a record whose constructor parameter names match entity properties. */
    List<CustomerView> findViewsByEmailEndingWithOrderByNameAsc(String domain);

    // --- Paging, slicing, scrolling --------------------------------------------------------------

    /** Page = content query + a COUNT query (unless Spring can work the total out from the content). */
    Page<Customer> findPageByNameStartingWith(String prefix, Pageable pageable);

    /** Slice = content query only, fetching size + 1 rows to know whether there is a next slice. */
    Slice<Customer> findSliceByNameStartingWith(String prefix, Pageable pageable);

    /** Keyset scrolling: WHERE id > :lastSeenId instead of OFFSET. The sort must be unique and stable. */
    Window<Customer> findFirst2ByOrderByIdAsc(ScrollPosition position);
}
