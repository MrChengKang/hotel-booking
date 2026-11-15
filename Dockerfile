FROM maven:3.10-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 stay && adduser -S -D -H -u 10001 -G stay stay \
    && mkdir -p /app /var/lib/stay/hotel-images \
    && chown -R stay:stay /app /var/lib/stay
WORKDIR /app
COPY --from=build --chown=stay:stay /build/target/hotel-booking-0.0.1-SNAPSHOT.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
ENV STAY_MEDIA_DIRECTORY=/var/lib/stay/hotel-images
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"
USER stay
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=7s --start-period=45s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${PORT:-8080}/api/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
