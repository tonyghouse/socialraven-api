# ---------- 1️⃣ Build Stage ----------
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# ── Build socialraven-common first (installs to container .m2) ──
WORKDIR /build/socialraven-common
COPY socialraven-common/pom.xml .
RUN mvn dependency:go-offline -q
COPY socialraven-common/src ./src
RUN mvn clean install -DskipTests -q

# ── Build socialraven-api ────────────────────────────────────────
WORKDIR /build/socialraven-api
COPY socialraven-api/pom.xml .
RUN mvn dependency:go-offline -q
COPY socialraven-api/src ./src
RUN mvn clean package -DskipTests


# ---------- 2️⃣ Runtime Stage ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /build/socialraven-api/target/socialraven-api.jar app.jar

# JVM tuning for small instance (important!)
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
