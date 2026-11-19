package dev.roadmap.aws.payment;

import static org.assertj.core.api.Assertions.assertThat;

import dev.roadmap.aws.AwsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

class PaymentSecretTest extends AwsIntegrationTest {

	@Autowired
	private PaymentApiProperties payment;

	@Autowired
	private ConfigurableEnvironment environment;

	@Test
	void secretsManagerSecret_isBoundToTypedConfig() {
		assertThat(payment.apiKey()).isEqualTo("pk_test_123");
		assertThat(payment.webhookSecret()).isEqualTo("whsec_456");
	}

	@Test
	void secretArrives_asItsOwnPropertySource() {
		assertThat(environment.getPropertySources().stream().map(PropertySource::getName))
			.anyMatch(name -> name.contains("/lab/payment-api"));
	}

}
