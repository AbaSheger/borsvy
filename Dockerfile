# Use a base image with Java 17
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy the backend code into the container
COPY backend /app/backend

# Grant execute permissions to the Maven wrapper and build the project
RUN cd backend && chmod +x mvnw && ./mvnw clean package -DskipTests

# Expose the port the application runs on
EXPOSE 8080

# Define the command to run the application with memory limits and production profile
CMD java -Xmx256m -Xms128m -XX:MaxMetaspaceSize=64m -XX:+UseSerialGC -XX:+UseCompressedOops -XX:+OptimizeStringConcat -XX:+UseStringDeduplication -jar /app/backend/target/*.jar --spring.profiles.active=prod