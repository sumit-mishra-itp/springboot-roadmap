package dev.roadmap.shop.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every finder is scoped by customer id: ownership is part of the query, so another customer's order simply does
 * not exist for the caller (404, never 403, so ids can't be probed).
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** No @EntityGraph on lines here: fetching a collection with LIMIT makes Hibernate paginate in memory. */
    Page<Order> findByCustomerId(long customerId, Pageable pageable);

    @EntityGraph(attributePaths = "lines")
    Optional<Order> findByIdAndCustomerId(long id, long customerId);
}
