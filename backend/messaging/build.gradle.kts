plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    implementation(project(":aws-clients"))
    implementation("org.springframework:spring-context")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(libs.aws.sqs)
    implementation("io.opentelemetry:opentelemetry-api:1.64.0")
}
