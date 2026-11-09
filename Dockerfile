# Single-container image: the React app is built and packaged into the Spring Boot jar, so one web service serves
# both the UI and the API. Used for Render / Railway / Fly / any container host. (docker-compose uses the per-service
# Dockerfiles in backend/ and frontend/ instead.)

FROM node:22-alpine AS ui
WORKDIR /ui
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --no-audit --no-fund; fi
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS api
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=ui /ui/dist ./src/main/resources/static
RUN mvn -B -q -Dmaven.test.skip=true package && cp target/queryai-backend-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S queryai && adduser -S queryai -G queryai && apk add --no-cache wget
WORKDIR /app
COPY --from=api /build/app.jar app.jar
USER queryai
ENV PORT=8080
EXPOSE 8080
# small-instance friendly defaults (512 MB free tiers): serial GC, capped heap share, smaller thread stacks
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
