package app.platform.domain.port;

import app.platform.domain.model.Manifest;

/** Read side of the S3 template registry (S3 is a downstream cache; Git is the truth). */
public interface TemplateStore {

    String fetchBody(String s3Key);

    String fetchSchema(String s3Key);

    Manifest fetchManifest(String s3Key);
}
