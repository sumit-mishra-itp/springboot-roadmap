package dev.roadmap.aws;

import java.util.Map;

import io.awspring.cloud.testcontainers.LocalstackAwsClientFactory;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * One LocalStack container per test JVM, provisioned the way Terraform would provision real AWS.
 *
 * <p>Why a static holder and not only a {@code @ServiceConnection} bean? {@code spring.config.import=aws-secretsmanager:...}
 * is resolved while Boot builds the {@code Environment}, before any bean (including a container bean) exists. So the
 * container must already run, the secret must already exist, and the Secrets Manager endpoint must already be
 * visible as a property when the context starts. System properties are the earliest property source there is.
 */
final class LocalStackInfra {

	/**
	 * 4.14.0 (2026-02-26) is the newest localstack/localstack tag that starts WITHOUT an auth token. From 2026.03.0
	 * (calendar versions, March 2026) every tag needs LOCALSTACK_AUTH_TOKEN. With a token you can move to a calendar tag.
	 */
	static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.14.0");

	static final String DLQ = "orders-dlq";

	static final int MAX_RECEIVE_COUNT = 2;

	static final String PAYMENT_SECRET = "{\"api-key\":\"pk_test_123\",\"webhook-secret\":\"whsec_456\"}";

	private static final LocalStackContainer CONTAINER = startAndProvision();

	private LocalStackInfra() {
	}

	static LocalStackContainer container() {
		return CONTAINER;
	}

	private static LocalStackContainer startAndProvision() {
		var container = new LocalStackContainer(IMAGE).withServices("s3", "sqs", "sns", "secretsmanager")
			// LocalStack skips presigned-URL signature checks by default. Real S3 never does, so turn them on.
			.withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0");
		var token = System.getenv("LOCALSTACK_AUTH_TOKEN");
		if (token != null && !token.isBlank()) {
			container.withEnv("LOCALSTACK_AUTH_TOKEN", token);
		}
		container.start();
		provision(new LocalstackAwsClientFactory(container));
		exposeToConfigDataLoader(container);
		return container;
	}

	private static void provision(LocalstackAwsClientFactory aws) {
		try (SecretsManagerClient secrets = aws.create(SecretsManagerClient.builder());
				S3Client s3 = aws.create(S3Client.builder());
				SqsClient sqs = aws.create(SqsClient.builder());
				SnsClient sns = aws.create(SnsClient.builder())) {
			secrets.createSecret(r -> r.name("/lab/payment-api").secretString(PAYMENT_SECRET));
			s3.createBucket(r -> r.bucket("lab-uploads"));
			provisionOrdersQueueWithDlq(sqs);
			provisionFanOut(sqs, sns);
		}
	}

	/** orders -> orders-dlq after MAX_RECEIVE_COUNT failed receives. A 1 s visibility timeout keeps the test fast. */
	private static void provisionOrdersQueueWithDlq(SqsClient sqs) {
		var dlqArn = queueArn(sqs, sqs.createQueue(r -> r.queueName(DLQ)).queueUrl());
		var redrivePolicy = "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}".formatted(dlqArn, MAX_RECEIVE_COUNT);
		sqs.createQueue(r -> r.queueName("orders")
			.attributes(Map.of(QueueAttributeName.VISIBILITY_TIMEOUT, "1", QueueAttributeName.REDRIVE_POLICY, redrivePolicy)));
	}

	/**
	 * order-events topic -> billing queue (raw delivery) and shipping queue (SNS envelope). On real AWS each queue
	 * also needs an access policy that lets sns.amazonaws.com call SendMessage for this topic ARN. LocalStack does not
	 * enforce IAM by default, so that policy is omitted here.
	 */
	private static void provisionFanOut(SqsClient sqs, SnsClient sns) {
		var topicArn = sns.createTopic(r -> r.name("order-events")).topicArn();
		subscribe(sqs, sns, topicArn, "billing", true);
		subscribe(sqs, sns, topicArn, "shipping", false);
	}

	private static void subscribe(SqsClient sqs, SnsClient sns, String topicArn, String queue, boolean raw) {
		var arn = queueArn(sqs, sqs.createQueue(r -> r.queueName(queue)).queueUrl());
		sns.subscribe(r -> r.topicArn(topicArn)
			.protocol("sqs")
			.endpoint(arn)
			.attributes(Map.of("RawMessageDelivery", String.valueOf(raw))));
	}

	private static String queueArn(SqsClient sqs, String queueUrl) {
		return sqs
			.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN))
			.attributes()
			.get(QueueAttributeName.QUEUE_ARN);
	}

	/** Dummy test/test credentials only. These reach the Secrets Manager client that config import builds. */
	private static void exposeToConfigDataLoader(LocalStackContainer container) {
		System.setProperty("spring.cloud.aws.secretsmanager.endpoint", container.getEndpoint().toString());
		System.setProperty("spring.cloud.aws.credentials.access-key", container.getAccessKey());
		System.setProperty("spring.cloud.aws.credentials.secret-key", container.getSecretKey());
		System.setProperty("spring.cloud.aws.region.static", container.getRegion());
		// The endpoint is an IP:port, so virtual-host URLs (bucket.127.0.0.1) cannot resolve. Real S3 wants virtual-host.
		System.setProperty("spring.cloud.aws.s3.path-style-access-enabled", "true");
	}

}
