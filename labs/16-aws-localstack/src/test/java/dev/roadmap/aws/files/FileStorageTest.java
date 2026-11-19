package dev.roadmap.aws.files;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import dev.roadmap.aws.AwsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FileStorageTest extends AwsIntegrationTest {

	// A plain JDK client with no AWS SDK and no credentials: it plays the browser in the direct-upload pattern.
	private static final HttpClient browser = HttpClient.newHttpClient();

	@Autowired
	private FileStorage storage;

	@Test
	void uploadedObject_canBeDownloadedAgain() throws Exception {
		storage.upload("docs/hello.txt", new ByteArrayInputStream("hello s3".getBytes(UTF_8)), "text/plain");

		assertThat(new String(storage.download("docs/hello.txt"), UTF_8)).isEqualTo("hello s3");
	}

	@Test
	void presignedUrls_letAPlainHttpClientUploadAndDownload() throws Exception {
		var key = "avatars/" + UUID.randomUUID() + ".png";
		var bytes = new byte[] { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 };

		var putUrl = storage.presignUpload(key, "image/png").toURI();
		var put = browser.send(put(putUrl, "image/png", bytes), BodyHandlers.discarding());

		assertThat(putUrl.getQuery()).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256", "X-Amz-Expires=300", "X-Amz-Signature=");
		assertThat(put.statusCode()).isEqualTo(200);
		assertThat(storage.download(key)).isEqualTo(bytes);

		var get = browser.send(HttpRequest.newBuilder(storage.presignDownload(key).toURI()).build(),
				BodyHandlers.ofByteArray());
		assertThat(get.statusCode()).isEqualTo(200);
		assertThat(get.body()).isEqualTo(bytes);
	}

	@Test
	void presignedUpload_isRejected_whenClientSendsADifferentContentType() throws Exception {
		var putUrl = storage.presignUpload("avatars/" + UUID.randomUUID() + ".png", "image/png").toURI();

		HttpResponse<String> put = browser.send(put(putUrl, "text/html", "<script>".getBytes(UTF_8)),
				BodyHandlers.ofString());

		assertThat(put.statusCode()).isEqualTo(403);
		assertThat(put.body()).contains("SignatureDoesNotMatch");
	}

	private static HttpRequest put(URI url, String contentType, byte[] body) {
		return HttpRequest.newBuilder(url).header("Content-Type", contentType).PUT(BodyPublishers.ofByteArray(body)).build();
	}

}
