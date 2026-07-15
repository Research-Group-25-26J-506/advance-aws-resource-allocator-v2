package app.platform.worker.handler;

import app.platform.domain.error.UnmappedFieldException;
import app.platform.templatesync.ManifestParser;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * Centralised retryable-vs-terminal classification (3.04 enhancement).
 * Throttling/5xx/network = RETRYABLE; validation/AccessDenied = terminal.
 */
@Component
public class AwsErrorClassifier {

    public enum Classification {
        RETRYABLE,
        VALIDATION,
        TERMINAL
    }

    public Classification classify(Throwable t) {
        if (t instanceof UnmappedFieldException || t instanceof ManifestParser.InvalidManifestException) {
            return Classification.VALIDATION;
        }
        if (t instanceof AwsServiceException aws) {
            int status = aws.statusCode();
            if (aws.isThrottlingException() || status >= 500) {
                return Classification.RETRYABLE;
            }
            String code = aws.awsErrorDetails() == null ? "" : aws.awsErrorDetails().errorCode();
            if ("ValidationError".equals(code)) {
                return Classification.VALIDATION;
            }
            return Classification.TERMINAL; // AccessDenied, AlreadyExists, limit errors, ...
        }
        if (t instanceof SdkClientException) {
            return Classification.RETRYABLE; // network-level: timeouts, connection resets
        }
        return Classification.TERMINAL;
    }
}
