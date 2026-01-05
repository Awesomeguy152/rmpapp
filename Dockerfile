FROM eclipse-temurin:17-jdk as build
WORKDIR /app
COPY . /build
WORKDIR /build/backend
RUN chmod +x gradlew && ./gradlew --no-daemon clean fatJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/backend/build/libs/*-all.jar /app/app.jar
COPY --from=build /build/backend/openapi.yaml /app/openapi.yaml
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "/app/app.jar", "com.example.ApplicationKt"]
