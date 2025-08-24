# -------------------------
# Stage 1: build
# -------------------------
FROM node:20-alpine AS build-wa

# Создаем рабочую директорию
WORKDIR /usr/src/app

# Копируем package.json и package-lock.json
COPY package*.json ./

# Устанавливаем только production зависимости
RUN npm ci --omit=dev

# Копируем исходники
COPY . .

# Создаем папку для сессий
RUN mkdir -p /usr/src/app/sessions

# -------------------------
# Stage 2: runtime
# -------------------------
FROM node:20-alpine AS runtime-wa

WORKDIR /usr/src/app

# Копируем зависимости и исходники из build stage
COPY --from=build-wa /usr/src/app/node_modules ./node_modules
COPY --from=build-wa /usr/src/app ./

# Создаем папку для сессий и выставляем права на пользователя node
RUN mkdir -p /usr/src/app/sessions && chown -R node:node /usr/src/app

# Открываем порт
EXPOSE 3000

# Запускаем под non-root пользователем
USER node

# Переменные окружения (можно переопределить в docker-compose)
ENV PORT=3000

# Запуск приложения
CMD ["node", "index.js"]
