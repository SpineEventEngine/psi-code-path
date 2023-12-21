val kotlinVersion = "1.9.21"

plugins {
    kotlin("jvm") version "1.9.21"
}

group = "io.spine.tools"
version = "2.0.0-SNAPSHOT.01"

repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    mavenCentral()
}

val intellijVersion = "213.7172.53"
    // "233.11799.300"
    // "213.7172.53"

configurations.all {
    resolutionStrategy {
        failOnVersionConflict()
        cacheChangingModulesFor(0, "seconds")
        force(
            "org.jetbrains.intellij.deps:trove4j:1.0.20181211",
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
            "org.jetbrains:annotations:24.0.0",
            "com.jetbrains.intellij.platform:core:$intellijVersion",
            "com.jetbrains.intellij.platform:util:$intellijVersion",
            "com.jetbrains.intellij.java:java-psi:$intellijVersion",
            "com.jetbrains.intellij.java:java-psi-impl:$intellijVersion"
        )
    }
}

dependencies {
    api("com.jetbrains.intellij.java:java-psi:$intellijVersion")
    api("com.jetbrains.intellij.java:java-psi-impl:$intellijVersion")

    implementation("com.jetbrains.intellij.platform:core:$intellijVersion")
    implementation("com.jetbrains.intellij.platform:util:$intellijVersion")

    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
//    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
