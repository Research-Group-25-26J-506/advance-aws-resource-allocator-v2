plugins {
    java
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.spotless)
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    group = "app.platform"
    version = "0.1.0-SNAPSHOT"

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            mavenBom("software.amazon.awssdk:bom:2.28.11")
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            palantirJavaFormat()
            removeUnusedImports()
        }
    }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
