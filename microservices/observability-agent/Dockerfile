FROM eclipse-temurin:21-jdk-alpine

LABEL maintainer="amollimaye"

VOLUME /tmp

EXPOSE 8080

ARG JAR_FILE=target/observability-agent-0.0.1-SNAPSHOT.jar

ADD ${JAR_FILE} observability-agent.jar
RUN addgroup -S spring && adduser -S spring -G spring && chown spring:spring /observability-agent.jar

USER spring

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/observability-agent.jar"]
