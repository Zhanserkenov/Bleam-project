# -------------------------
# Stage 1: build with Maven
# -------------------------
FROM maven:3.9.4-eclipse-temurin-21-alpine AS builder

# Рабочая директория внутри сборочного контейнера
WORKDIR /workspace

# Копируем только pom.xml для кеширования зависимостей
COPY pom.xml ./

# (Опционально) если у вас есть файл settings.xml — скопируйте и используйте его
# COPY .mvn/settings.xml /root/.m2/settings.xml

# Заранее загрузим зависимости в кэш, чтобы последующие сборки были быстрее
RUN mvn -B dependency:go-offline

# Копируем исходники
COPY src ./src

# Собираем production-jar (skip tests для ускорения, в CI можно запускать тесты отдельно)
RUN mvn -B -DskipTests package

# -------------------------
# Stage 2: runtime image
# -------------------------
# Используем проверенный runtime для Java 21
FROM eclipse-temurin:21-jre-jammy

# Рабочая директория в runtime
WORKDIR /app

# Копируем собранный jar (подставляется первый найденный jar в target)
COPY --from=builder /workspace/target/*.jar app.jar

# Пробрасываем порт (если ваше приложение слушает 8080)
EXPOSE 8080

# Рекомендуемые переменные среды (можно переопределять через docker-compose)
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx512m -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod

# Запуск под непользовательским пользователем (опционально)
# USER 1000:1000

# Entry point — запускаем spring-boot jar
ENTRYPOINT ["sh", "-c", "exec java $JAVA_TOOL_OPTIONS -jar /app/app.jar"]