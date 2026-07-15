plugins {
    // Auto-provisions the JDK 21 toolchain when the host runs a different Java
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "platform-backend"

include(
    "domain",
    "app-common",
    "messaging",
    "aws-clients",
    "persistence",
    "security",
    "observability",
    "templatesync",
    "api",
    "worker",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
