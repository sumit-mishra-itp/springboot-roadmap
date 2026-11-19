package dev.roadmap.aws;

import org.springframework.boot.SpringApplication;

/** {@code ./mvnw spring-boot:test-run}: the app against a provisioned LocalStack, no AWS account needed. */
public class TestAwsLocalstackApplication {

	public static void main(String[] args) {
		LocalStackInfra.container(); // start and provision before Boot resolves spring.config.import
		SpringApplication.from(AwsLocalstackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
