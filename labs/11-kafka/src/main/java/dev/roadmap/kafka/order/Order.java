package dev.roadmap.kafka.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String sku;

	private int quantity;

	private Instant createdAt;

	@Version
	private Long version;

	protected Order() {
	}

	Order(String sku, int quantity) {
		this.sku = sku;
		this.quantity = quantity;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public int getQuantity() {
		return quantity;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Order other && id != null && id.equals(other.id));
	}

	@Override
	public int hashCode() {
		return Order.class.hashCode();
	}

}
