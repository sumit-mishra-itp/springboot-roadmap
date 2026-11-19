package dev.roadmap.aws.files;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

import dev.roadmap.aws.LabProperties;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import org.springframework.stereotype.Service;

/**
 * Thin service over {@link S3Template}. The presign methods are the interesting part: the app signs
 * a short-lived URL, and the browser or mobile client moves the bytes straight to S3. The app server
 * never streams the file, and the client never holds AWS credentials.
 */
@Service
public class FileStorage {

	static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

	private final S3Template s3;

	private final String bucket;

	public FileStorage(S3Template s3, LabProperties props) {
		this.s3 = s3;
		this.bucket = props.bucket();
	}

	public void upload(String key, InputStream content, String contentType) {
		s3.upload(bucket, key, content, ObjectMetadata.builder().contentType(contentType).build());
	}

	public byte[] download(String key) throws IOException {
		try (InputStream in = s3.download(bucket, key).getInputStream()) {
			return in.readAllBytes();
		}
	}

	/** The Content-Type is part of the signature, so the client must send exactly this header. */
	public URL presignUpload(String key, String contentType) {
		return s3.createSignedPutURL(bucket, key, PRESIGN_TTL, null, contentType);
	}

	public URL presignDownload(String key) {
		return s3.createSignedGetURL(bucket, key, PRESIGN_TTL);
	}

}
