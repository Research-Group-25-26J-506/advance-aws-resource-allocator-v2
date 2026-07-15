# Build context = backend/.
FROM eclipse-temurin:21-jre AS extract
WORKDIR /app
COPY worker/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 platform
WORKDIR /app
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
