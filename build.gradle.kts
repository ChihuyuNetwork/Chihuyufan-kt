import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.3.2"
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
    implementation("dev.kord:kord-core:0.9.0")
    implementation(platform("com.aallam.openai:openai-client-bom:3.2.2"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("com.aallam.openai:openai-client")
    implementation("com.mattmalec:Pterodactyl4J:2.BETA_140")
    implementation("org.xerial:sqlite-jdbc:3.41.2.1")
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
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