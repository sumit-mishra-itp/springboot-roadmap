package dev.roadmap.jpa.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {

    @Query("""
            select new dev.roadmap.jpa.order.OrderTotal(o.id, sum(l.unitPrice * l.quantity))
            from PurchaseOrder o join o.lines l
            group by o.id
            order by o.id""")
    List<OrderTotal> findTotals();
}
