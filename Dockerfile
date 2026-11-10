FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 epos
COPY --from=build /workspace/target/epos-api-*.jar app.jar
USER epos
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
