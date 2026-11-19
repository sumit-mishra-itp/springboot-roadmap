package dev.roadmap.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;

import io.awspring.cloud.autoconfigure.config.secretsmanager.AwsSecretsManagerPropertySourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

/**
 * Starts the real application by hand (no test context) to show what config import does when a secret is missing.
 * Listener containers stay stopped so they do not compete with the shared test context for messages.
 */
class SecretImportFailFastTest {

	@Test
	void missingRequiredSecret_failsStartup() {
		assertThatThrownBy(() -> start("aws-secretsmanager:/lab/does-not-exist").close())
			.isInstanceOf(AwsSecretsManagerPropertySourceNotFoundException.class)
			.hasRootCauseInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void missingOptionalSecret_isSkipped() {
		try (var context = start("optional:aws-secretsmanager:/lab/does-not-exist")) {
			assertThat(context.isRunning()).isTrue();
		}
	}

	/**
	 * The @ServiceConnection LocalStack bean is on the sources list, yet the import still dials the broken endpoint
	 * property: config data is loaded before any bean, so connection details cannot reach it.
	 */
	@Test
	void configImport_ignoresServiceConnection_andUsesOnlyProperties() {
		var app = new SpringApplicationBuilder(AwsLocalstackApplication.class, TestcontainersConfiguration.class);

		assertThatThrownBy(() -> app.run("--spring.cloud.aws.secretsmanager.endpoint=http://localhost:1").close())
			.hasRootCauseInstanceOf(ConnectException.class);
	}

	private static ConfigurableApplicationContext start(String extraImport) {
		var endpoint = LocalStackInfra.container().getEndpoint().toString();
		return new SpringApplicationBuilder(AwsLocalstackApplication.class).run(
				"--spring.config.import=" + extraImport,
				"--spring.cloud.aws.endpoint=" + endpoint,
				"--spring.cloud.aws.sqs.listener.auto-startup=false");
	}

}
