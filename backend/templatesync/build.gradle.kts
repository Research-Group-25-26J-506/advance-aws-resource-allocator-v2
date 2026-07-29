plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    implementation(project(":aws-clients"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation(libs.json.schema.validator)
    implementation(libs.aws.s3)
    implementation(libs.aws.dynamodb)
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.yaml:snakeyaml")
    implementation("org.springframework:spring-jdbc")
}
