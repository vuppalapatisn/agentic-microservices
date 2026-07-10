# Stage 1: Build React UI (same as Python version)
FROM node:20-alpine AS ui-build
WORKDIR /ui
COPY ui/package.json ui/package-lock.json* ./
RUN npm install
COPY ui/ ./
RUN npm run build

# Stage 2: Build Java application
FROM eclipse-temurin:21-jdk-alpine AS java-build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && \
    mvn -B package -DskipTests

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache ca-certificates

# Copy built JAR
COPY --from=java-build /build/target/observability-debug-agent-*.jar app.jar

# Copy compiled React UI into static resources
COPY --from=ui-build /ui/dist ./static

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

EXPOSE 8092

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.web.resources.static-locations=file:/app/static/"]
