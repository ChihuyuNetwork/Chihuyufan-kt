import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
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
    implementation("dev.kord:kord-common:0.13.1")
    implementation("dev.kord:kord-core:0.13.1")
    implementation("dev.kord:kord-core-voice:0.13.1")
    implementation("dev.kord:kord-rest:0.13.1")
    implementation("dev.kord:kord-voice:0.13.1")
    implementation("dev.kord:kord-gateway:0.13.1")
    implementation(platform("com.aallam.openai:openai-client-bom:3.7.0"))
    implementation("io.ktor:ktor-client-okhttp-jvm:2.3.9")
    implementation("com.aallam.openai:openai-client")
    implementation("com.mattmalec:Pterodactyl4J:2.BETA_140")
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
    jvmToolchain(17)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "17"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "17"
}
