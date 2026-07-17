package app.platform.worker.aws;

import app.platform.domain.port.StackLauncher;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudformation.CloudFormationClient;
import software.amazon.awssdk.services.cloudformation.model.Capability;
import software.amazon.awssdk.services.cloudformation.model.CreateStackRequest;
import software.amazon.awssdk.services.cloudformation.model.OnFailure;
import software.amazon.awssdk.services.cloudformation.model.Parameter;
import software.amazon.awssdk.services.cloudformation.model.Tag;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.Credentials;

/**
 * Real CloudFormation driver: STS-assumes the per-template execution role (3.06), then
 * CreateStack with ClientRequestToken = idempotency key so CFN dedupes retries.
 */
@Component
@Profile("!local")
public class CfnStackLauncher implements StackLauncher {

    private final StsClient sts;

    public CfnStackLauncher(StsClient sts) {
        this.sts = sts;
    }

    @Override
    public String createStack(StackLaunch launch) {
        CloudFormationClient cfn = clientFor(launch.executionRoleArn(), launch.region(), launch.clientRequestToken());
        clearRolledBackStack(cfn, launch.stackName());
        CreateStackRequest.Builder builder = CreateStackRequest.builder()
                .stackName(launch.stackName())
                .clientRequestToken(launch.clientRequestToken())
                .onFailure(OnFailure.ROLLBACK)
                .capabilities(launch.capabilities().stream()
                        .map(Capability::fromValue)
                        .toList())
                .parameters(launch.parameters().entrySet().stream()
                        .map(e -> Parameter.builder()
                                .parameterKey(e.getKey())
                                .parameterValue(e.getValue())
                                .build())
                        .collect(Collectors.toList()))
                .tags(launch.tags().stream()
                        .map(t -> Tag.builder().key(t.key()).value(t.value()).build())
                        .collect(Collectors.toList()));
        if (launch.templateBody() != null) {
            builder.templateBody(launch.templateBody());
        } else {
            builder.templateURL(launch.templateUrl());
        }
        return cfn.createStack(builder.build()).stackId();
    }

    @Override
    public void deleteStack(String stackId, String executionRoleArn, app.platform.domain.model.Environment env,
            String region) {
        clientFor(executionRoleArn, region, null).deleteStack(b -> b.stackName(stackId));
    }

    /**
     * A stack whose CREATE rolled back holds no resources but blocks re-creation under the same
     * name. On retry, delete it first so CreateStack can proceed.
     */
    private void clearRolledBackStack(CloudFormationClient cfn, String stackName) {
        try {
            var stacks = cfn.describeStacks(b -> b.stackName(stackName)).stacks();
            if (!stacks.isEmpty()
                    && stacks.get(0).stackStatus()
                            == software.amazon.awssdk.services.cloudformation.model.StackStatus.ROLLBACK_COMPLETE) {
                cfn.deleteStack(b -> b.stackName(stackName));
                cfn.waiter().waitUntilStackDeleteComplete(b -> b.stackName(stackName));
            }
        } catch (software.amazon.awssdk.services.cloudformation.model.CloudFormationException e) {
            // stack not found — nothing to clear
        }
    }

    private CloudFormationClient clientFor(String executionRoleArn, String region, String sessionSuffix) {
        var builder = CloudFormationClient.builder().region(Region.of(region));
        if (executionRoleArn != null && !executionRoleArn.isBlank()) {
            Credentials credentials = sts.assumeRole(b -> b.roleArn(executionRoleArn)
                            .roleSessionName("platform-worker-"
                                    + (sessionSuffix == null ? "delete" : sessionSuffix.substring(0, 8)))
                            .durationSeconds(900))
                    .credentials();
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsSessionCredentials.create(
                    credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken())));
        }
        return builder.build();
    }
}
