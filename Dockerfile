FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/e4s-index-system-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
