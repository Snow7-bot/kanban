# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17-alpine AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre-alpine

LABEL version="1.0.0" description="康伴智能医疗助手"

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
