FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/task-api-1.0.0.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080 8081
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]


