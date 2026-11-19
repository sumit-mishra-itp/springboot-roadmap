package dev.roadmap.kafka.outbox;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Instant;
import java.util.UUID;

@Entity
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String aggregateType;

	private UUID aggregateId;

	private String eventType;

	private String topic;

	private String payload;

	private Instant createdAt;

	private Instant sentAt;

	protected OutboxEvent() {
	}

	public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String topic, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.topic = topic;
		this.payload = payload;
		this.createdAt = Instant.now();
	}

	void markSent(Instant when) {
		this.sentAt = when;
	}

	UUID getAggregateId() {
		return aggregateId;
	}

	String getTopic() {
		return topic;
	}

	String getPayload() {
		return payload;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof OutboxEvent other && id != null && id.equals(other.id));
	}

	@Override
	public int hashCode() {
		return OutboxEvent.class.hashCode();
	}

}
