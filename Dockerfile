FROM eclipse-temurin:17-jdk

# Set display port to avoid errors
ENV DISPLAY=:99

# Copy your app jar
ARG JAR_FILE=stripe-payment-demo-1.0-runner.jar
COPY ${JAR_FILE} app.jar

EXPOSE 3121
ENTRYPOINT ["java", "-Xmx256m", "-XX:+UseG1GC", "-Dquarkus.http.port=3121", "-jar", "/app.jar"]