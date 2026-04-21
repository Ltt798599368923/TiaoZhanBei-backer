# 构建阶段：用 Maven 编译打包
FROM maven:3.9.9-eclipse-temurin-17-focal AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 运行阶段：用轻量级 JDK 运行 jar 包
FROM eclipse-temurin:17-jdk-alpine
ARG PORT=8080
ENV PORT=${PORT}
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE ${PORT}
CMD ["sh", "-c", "java -jar /app/app.jar --server.port=${PORT}"]