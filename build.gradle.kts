plugins {
    kotlin("jvm") version "2.0.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "love.chihuyu"
version = ""

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.mattmalec.com/repository/releases")
    maven("https://jitpack.io")
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.23.1")
    implementation("dev.kord:kord-common:0.15.0")
    implementation("dev.kord:kord-core:0.15.0")
    implementation("dev.kord:kord-core-voice:0.15.0")
    implementation("dev.kord:kord-rest:0.15.0")
    implementation("dev.kord:kord-voice:0.15.0")
    implementation("dev.kord:kord-gateway:0.15.0")
    implementation("com.mattmalec:Pterodactyl4J:2.BETA_142")
    implementation(kotlin("stdlib"))
}

ktlint {
    ignoreFailures.set(true)
    disabledRules.add("no-wildcard-imports")
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "love.chihuyu.BotKt"
    }

    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(21)
}
