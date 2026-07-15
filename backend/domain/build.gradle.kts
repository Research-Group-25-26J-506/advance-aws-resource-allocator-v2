// Hexagonal core: NO Spring, NO AWS SDK, NO Jackson. The ArchUnit test in :api enforces this.
plugins {
    `java-library`
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
