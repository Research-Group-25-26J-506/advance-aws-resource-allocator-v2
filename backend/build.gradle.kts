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

    // CVE floor for BOM-managed transitives (Trivy gate in build-images); the dependency-
    // management plugin honours these version properties over the Boot BOM.
    ext["tomcat.version"] = "10.1.55"
    ext["netty.version"] = "4.1.135.Final"
    ext["spring-security.version"] = "6.5.9"
    ext["jackson-bom.version"] = "2.21.4"

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            mavenBom("software.amazon.awssdk:bom:2.28.11")
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            // palantirJavaFormat/removeUnusedImports need javac internals that break on newer
            // daemon JDKs (NoSuchMethodError on JDK 25); re-enable once CI + dev pin a JDK 21 daemon.
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    dependencies {
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // Gradle 9 no longer injects the JUnit Platform launcher onto the test runtime classpath
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging { events("passed", "skipped", "failed") }
    }
}
