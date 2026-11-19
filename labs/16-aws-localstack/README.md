# Lab 16: Spring Cloud AWS 4 on LocalStack (S3, SQS, SNS, Secrets Manager)

Companion to the note [`notes/16-aws/aws-spring.md`](../../notes/16-aws/aws-spring.md).
Stack: Spring Boot 4.1.2, Spring Cloud AWS 4.2.2 (AWS SDK for Java 2.57.0), Jackson 3, Testcontainers 2.0.5,
LocalStack `4.14.0` (community image), Java 21. **No AWS account, no real credentials**: everything runs against
LocalStack with the dummy `test`/`test` keys.

## What it demonstrates

| Package | What |
|---|---|
| `files` | `FileStorage` over `S3Template`: upload/download, plus `createSignedPutURL` / `createSignedGetURL` presigned URLs (5 min TTL, Content-Type signed into the PUT) |
| `orders` | `OrderPublisher` sends `OrderPlaced` straight to the `orders` queue (`SqsTemplate`) or publishes it to the `order-events` topic (`SnsTemplate`). `OrderListeners` has three `@SqsListener`s: `orders`, `billing` (raw delivery) and `shipping` (SNS envelope, unwrapped by `@SnsNotificationMessage`). `OrderProcessor.fulfil` throws for `quantity <= 0`, which makes a poison message |
| `payment` | `PaymentApiProperties`: a validated `@ConfigurationProperties` record bound from the Secrets Manager secret `/lab/payment-api` through `spring.config.import=aws-secretsmanager:/lab/payment-api?prefix=payment.` |
| root | `LabProperties` (bucket, queue and topic names). The app never creates AWS resources: that is the job of Terraform/CDK, played by `LocalStackInfra` in the tests |

Test infrastructure (`src/test`):
- `LocalStackInfra` starts one LocalStack per JVM and provisions everything *before* Spring starts: the secret, the bucket, `orders` with a redrive policy to `orders-dlq` (`maxReceiveCount=2`, visibility timeout 1 s), and the `order-events` topic with two SQS subscriptions. It also publishes the endpoint, region and dummy keys as system properties, because `spring.config.import` runs before any bean exists. It turns on `S3_SKIP_SIGNATURE_VALIDATION=0`, because LocalStack does not check presigned signatures by default.
- `TestcontainersConfiguration` exposes the same container as a `@ServiceConnection` bean. `spring-cloud-aws-testcontainers` turns it into `AwsConnectionDetails` for every auto-configured client.

## Run it

```bash
./mvnw verify                                  # Docker must be running
./mvnw test -Dtest=OrderQueueTest
./mvnw spring-boot:test-run                    # app + provisioned LocalStack; no HTTP endpoints, only the listeners run
```

Do **not** use `./mvnw spring-boot:run` here: with no endpoint override, the SDK's default provider chain would aim at
real AWS with whatever credentials your machine has.

## What each test proves

All Spring tests extend `AwsIntegrationTest`, so they share one cached context. A second context would start a second set
of listener containers that compete for the same queues.

| Test | Proves |
|---|---|
| `files.FileStorageTest` | `S3Template` upload then download round-trips the bytes. A presigned PUT URL (`X-Amz-Algorithm=AWS4-HMAC-SHA256`, `X-Amz-Expires=300`) lets a plain `java.net.http.HttpClient` with no AWS SDK and no credentials upload, and a presigned GET lets it download. A PUT with a **different Content-Type** than the one signed gets `403 SignatureDoesNotMatch` |
| `orders.OrderQueueTest` | `SqsTemplate.send` → `@SqsListener` round trip. A poison order is delivered **exactly `maxReceiveCount` (2) times**: with the default `ON_SUCCESS` ack mode a failed message is not deleted and reappears after the visibility timeout. Then SQS itself moves it to `orders-dlq`, where `SqsTemplate.receive` finds the same payload. The app contains no DLQ code |
| `orders.OrderFanOutTest` | One `SnsTemplate.sendNotification` reaches **both** subscribed queues: `billing` (raw delivery, body is the JSON) and `shipping` (SNS envelope, unwrapped by `@SnsNotificationMessage`). The `orders` queue, which isn't subscribed, gets nothing |
| `payment.PaymentSecretTest` | The JSON keys of the secret land in `PaymentApiProperties` (`payment.api-key`, `payment.webhook-secret`), and the secret shows up as its own `PropertySource` |
| `SecretImportFailFastTest` | A missing **required** secret fails startup with `AwsSecretsManagerPropertySourceNotFoundException` (root cause `ResourceNotFoundException`). The same secret with `optional:` is skipped and the app starts. A broken `spring.cloud.aws.secretsmanager.endpoint` fails startup even though a `@ServiceConnection` LocalStack bean is registered: config import reads properties only |

## LocalStack and the auth token (read this before bumping the image)

- Since **2026.03.0** (March 2026, calendar versioning) every `localstack/localstack` tag needs `LOCALSTACK_AUTH_TOKEN`.
  The community `4.x` line ended at **`4.14.0`** (2026-02-26), the newest tag that starts without one. This lab pins it.
  LocalStack's own docs use `4.12` as the pin example. Pinned community images get **no security patches or new AWS
  features**, so this is fine for a lab and a liability for a company CI.
- If `LOCALSTACK_AUTH_TOKEN` is set in your environment, `LocalStackInfra` passes it to the container, so you can switch
  `IMAGE` to a calendar tag (for example `2026.10`). Put the token in `.env` or your shell, never in git.
- Fallbacks if you can't pull `4.14.0`: `motoserver/moto` behind a `GenericContainer` with the endpoint override, or
  **Floci** (`floci/floci`), which Spring Cloud AWS 4.2.2 supports first-class (`AwsFlociContainerConnectionDetailsFactory`,
  Docker Compose support). That class is why the logs show a harmless
  `Failed to introspect ... AwsFlociContainerConnectionDetailsFactory: Type io.floci.testcontainers.FlociContainer not present` warning.
