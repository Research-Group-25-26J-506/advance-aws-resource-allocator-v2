# Layered jar for cacheable Docker layers (3.01). Build context = backend/.
FROM eclipse-temurin:25-jre AS extract
WORKDIR /app
COPY api/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:25-jre
# rm pebble: unused Ubuntu service manager in the base image that drags HIGH Go CVEs into scans
RUN useradd --system --uid 10001 platform && apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* /usr/bin/pebble
WORKDIR /app
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
