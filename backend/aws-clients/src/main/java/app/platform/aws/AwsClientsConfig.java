package app.platform.aws;

import java.net.URI;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * One place to build AWS SDK v2 clients. `platform.aws.endpoint-override` points everything at
 * LocalStack in the `local` profile; empty in real environments (default provider chain).
 */
@Configuration
public class AwsClientsConfig {

    @Value("${platform.aws.region:us-east-1}")
    private String region;

    @Value("${platform.aws.endpoint-override:}")
    private String endpointOverride;

    private <B extends AwsClientBuilder<B, ?>> Consumer<B> common() {
        return builder -> {
            builder.region(Region.of(region));
            if (!endpointOverride.isBlank()) {
                builder.endpointOverride(URI.create(endpointOverride));
                // LocalStack mode: pin dummy credentials so local runs NEVER read (or need)
                // the developer's real ~/.aws credentials.
                builder.credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")));
            }
        };
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder().applyMutation(common()).build();
    }

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder().applyMutation(common());
        if (!endpointOverride.isBlank()) {
            builder.forcePathStyle(true); // LocalStack needs path-style addressing
        }
        return builder.build();
    }

    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder().applyMutation(common()).build();
    }

    @Bean
    public CloudFormationClient cloudFormationClient() {
        return CloudFormationClient.builder().applyMutation(common()).build();
    }

    @Bean
    public StsClient stsClient() {
        return StsClient.builder().applyMutation(common()).build();
    }

    @Bean
    public SsmClient ssmClient() {
        return SsmClient.builder().applyMutation(common()).build();
    }
}
