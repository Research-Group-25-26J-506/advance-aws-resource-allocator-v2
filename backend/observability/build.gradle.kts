plugins {
    `java-library`
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.slf4j:slf4j-api")
    api(libs.logstash.encoder)
}
