# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:17-jre
RUN useradd -r -u 1001 app && mkdir -p /data/uploads && chown -R app /data
USER app
WORKDIR /app
COPY --from=build /src/target/assessment.war app.war
ENV STORAGE_DIR=/data/uploads SPRING_PROFILES_ACTIVE=prod JAVA_OPTS="-Xms256m -Xmx768m"
EXPOSE 8080
VOLUME /data
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -Duser.timezone=UTC -jar app.war"]
