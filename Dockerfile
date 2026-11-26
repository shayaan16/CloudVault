FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -g 10001 vault && useradd -u 10001 -g vault -m vault && mkdir -p /app/data/files && chown -R vault:vault /app
COPY --from=build --chown=vault:vault /build/target/cloudvault-1.0.0.jar app.jar
USER vault
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:InitialRAMPercentage=10 -XX:+UseSerialGC -XX:ActiveProcessorCount=2"
ENTRYPOINT ["java","-jar","app.jar"]
