plugins {
    `java-library`
}

dependencies {
    api(project(":domain"))
    // api, not implementation: SpringDataRepos exposes JpaRepository types to :api and :worker
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly(libs.mysql.connector)

    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.mysql)
}
