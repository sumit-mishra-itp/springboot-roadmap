package dev.roadmap.kafka.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * SKIP LOCKED lets several app instances run the relay at once. Each one locks a different
	 * batch instead of blocking on the same rows or publishing them twice.
	 */
	@Query(value = """
			select * from outbox_event
			where sent_at is null
			order by created_at
			limit :limit
			for update skip locked""", nativeQuery = true)
	List<OutboxEvent> lockUnsent(int limit);

}
