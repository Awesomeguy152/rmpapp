FROM eclipse-temurin:17-jdk as build
WORKDIR /app
COPY . /app
RUN chmod +x gradlew && ./gradlew --no-daemon clean fatJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar /app/app.jar
COPY --from=build /app/openapi.yaml /app/openapi.yaml
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "/app/app.jar", "com.example.ApplicationKt"]
