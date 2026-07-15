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
