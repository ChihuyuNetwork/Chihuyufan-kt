import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.5.1"
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
    implementation("dev.kord:kord-common:0.10.0")
    implementation("dev.kord:kord-core:0.10.0")
    implementation("dev.kord:kord-rest:0.10.0")
    implementation(platform("com.aallam.openai:openai-client-bom:3.3.2"))
    implementation("io.ktor:ktor-client-okhttp")
    implementation("com.aallam.openai:openai-client")
    implementation("com.mattmalec:Pterodactyl4J:2.BETA_140")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
    implementation("org.jetbrains.exposed:exposed-core:0.42.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.42.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.42.0")
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
