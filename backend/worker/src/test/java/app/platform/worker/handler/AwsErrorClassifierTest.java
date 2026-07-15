package app.platform.worker.handler;

import static org.assertj.core.api.Assertions.assertThat;

import app.platform.domain.error.UnmappedFieldException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

class AwsErrorClassifierTest {

    private final AwsErrorClassifier classifier = new AwsErrorClassifier();

    private AwsServiceException aws(int status, String code) {
        return AwsServiceException.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build())
                .build();
    }

    @Test
    void throttlingAndServerErrorsAreRetryable() {
        assertThat(classifier.classify(aws(400, "ThrottlingException")))
                .isEqualTo(AwsErrorClassifier.Classification.RETRYABLE);
        assertThat(classifier.classify(aws(503, "ServiceUnavailable")))
                .isEqualTo(AwsErrorClassifier.Classification.RETRYABLE);
        assertThat(classifier.classify(SdkClientException.create("connection reset")))
                .isEqualTo(AwsErrorClassifier.Classification.RETRYABLE);
    }

    @Test
    void validationAndAccessErrorsAreTerminal() {
        assertThat(classifier.classify(aws(400, "ValidationError")))
                .isEqualTo(AwsErrorClassifier.Classification.VALIDATION);
        assertThat(classifier.classify(aws(403, "AccessDenied")))
                .isEqualTo(AwsErrorClassifier.Classification.TERMINAL);
        assertThat(classifier.classify(new UnmappedFieldException(Set.of("rogue"))))
                .isEqualTo(AwsErrorClassifier.Classification.VALIDATION);
    }
}
