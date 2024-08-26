FROM eclipse-temurin:21.0.4_7-jdk-jammy as builder
WORKDIR /opt
COPY gradle ./gradle
COPY src ./src
COPY build.gradle.kts .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY gradlew .
RUN ./gradlew shadowJar

FROM eclipse-temurin:21.0.4_7-jdk-jammy
WORKDIR /opt
COPY --from=builder /opt/build/libs/chihuyufan-kt-all.jar .
CMD ["java", "-jar", "chihuyufan-kt-all.jar"]
