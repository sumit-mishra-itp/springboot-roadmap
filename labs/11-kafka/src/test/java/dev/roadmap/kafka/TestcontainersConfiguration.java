package dev.roadmap.kafka;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	// org.testcontainers.kafka.KafkaContainer runs Apache Kafka in KRaft mode (no ZooKeeper).
	// The GraalVM "kafka-native" image starts in about a second. 4.2.2 matches the kafka-clients version.
	// @ServiceConnection sets spring.kafka.bootstrap-servers through ApacheKafkaContainerConnectionDetailsFactory.
	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.2.2"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
