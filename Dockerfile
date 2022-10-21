FROM openjdk:8-alpine

COPY target/uberjar/kong-service.jar /kong-service/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/kong-service/app.jar"]
