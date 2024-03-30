FROM maven:3.9.6-eclipse-temurin-21 AS builder
COPY . .
RUN mvn clean package

# Create a Java runtime
FROM eclipse-temurin:21 AS jre-build

RUN $JAVA_HOME/bin/jlink \
         --add-modules java.base,java.logging,java.net.http \
         --strip-debug \
         --strip-java-debug-attributes \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

#FROM debian:stable-slim
FROM bitnami/minideb:bullseye

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=jre-build /javaruntime $JAVA_HOME
COPY --from=builder target/*-fat.jar app.jar
COPY webroot /webroot

EXPOSE 8888
ENTRYPOINT [ "java", "-jar", "app.jar" ]

