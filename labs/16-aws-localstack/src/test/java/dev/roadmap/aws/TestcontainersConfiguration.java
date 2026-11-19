package dev.roadmap.aws;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.localstack.LocalStackContainer;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	// spring-cloud-aws-testcontainers registers AwsLocalstackContainerConnectionDetailsFactory, which turns this bean
	// into AwsConnectionDetails (endpoint, region, access key, secret key). Every auto-configured client
	// (S3, SQS, SNS) then talks to LocalStack. Config import cannot use it: see LocalStackInfra.
	@Bean
	@ServiceConnection
	LocalStackContainer localStack() {
		return LocalStackInfra.container();
	}

}
