import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.1"
}

group = "love.chihuyu"
version = ""

repositories {
    mavenCentral()
    maven("https://repo.mattmalec.com/repository/releases")
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")
    implementation("dev.kord:kord-core:0.8.2")
    implementation(platform("com.aallam.openai:openai-client-bom:3.2.0"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("com.aallam.openai:openai-client")
    implementation("com.mattmalec:Pterodactyl4J:2.BETA_140")
    implementation(kotlin("stdlib-jdk8"))
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
    jvmToolchain(18)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "18"
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "18"
}