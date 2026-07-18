plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    api(libs.aws.sqs)
    api(libs.aws.s3)
    api(libs.aws.dynamodb)
    api(libs.aws.cloudformation)
    api(libs.aws.sts)
    api(libs.aws.ssm)
    api(libs.aws.cloudwatchlogs)
    api(libs.aws.codebuild)
    api(libs.aws.costexplorer)
    api(libs.aws.ecs)
}
