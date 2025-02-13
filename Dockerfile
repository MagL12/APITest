# Базовый образ с Maven и OpenJDK
FROM maven:3.9.4-eclipse-temurin-17 as build

# Копируем исходный код в контейнер
COPY . /app

# Переключаемся в директорию проекта
WORKDIR /app

# Устанавливаем зависимости и собираем проект
RUN mvn clean install

# Запускаем тесты
CMD ["mvn", "test"]