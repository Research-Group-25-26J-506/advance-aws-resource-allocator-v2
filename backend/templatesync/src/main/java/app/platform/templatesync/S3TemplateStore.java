package app.platform.templatesync;

import app.platform.domain.model.Manifest;
import app.platform.domain.port.TemplateStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/** Reads published template artefacts from the S3 registry (bootstrap's templates bucket). */
@Component
public class S3TemplateStore implements TemplateStore {

    private final S3Client s3;
    private final ManifestParser manifestParser;
    private final String bucket;

    public S3TemplateStore(
            S3Client s3, ManifestParser manifestParser, @Value("${platform.s3.templates-bucket}") String bucket) {
        this.s3 = s3;
        this.manifestParser = manifestParser;
        this.bucket = bucket;
    }

    @Override
    public String fetchBody(String s3Key) {
        return s3.getObjectAsBytes(b -> b.bucket(bucket).key(s3Key)).asUtf8String();
    }

    @Override
    public String fetchSchema(String s3Key) {
        return fetchBody(s3Key);
    }

    @Override
    public Manifest fetchManifest(String s3Key) {
        return manifestParser.parse(fetchBody(s3Key));
    }
}
