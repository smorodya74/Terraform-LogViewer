FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace


COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./


RUN mvn -B dependency:go-offline


COPY src/ ./src/


RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --create-home --shell /bin/bash appuser
COPY --from=build /workspace/target/terraform-log-viewer-0.2.0.jar app.jar
USER appuser
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]