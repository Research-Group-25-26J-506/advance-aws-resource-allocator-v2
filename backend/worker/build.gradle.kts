plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":app-common"))
    implementation(project(":persistence"))
    implementation(project(":messaging"))
    implementation(project(":aws-clients"))
    implementation(project(":observability"))
    implementation(project(":templatesync"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-web") // actuator probes over HTTP
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation(libs.otel.spring.starter)
    implementation(libs.jinjava)
    implementation(libs.logstash.encoder)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.localstack)
}

tasks.bootJar {
    layered { enabled = true }
}
