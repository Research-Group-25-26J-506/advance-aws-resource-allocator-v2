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
}
