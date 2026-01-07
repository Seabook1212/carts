#FROM java:openjdk-8-alpine
#FROM eclipse-temurin:8-jdk-alpine
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /usr/src/app
COPY ./target/*.jar ./app.jar

ENTRYPOINT ["java","-Djava.security.egd=file:/dev/urandom","-jar","./app.jar", "--port=8081"]
