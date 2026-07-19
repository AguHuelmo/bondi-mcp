# Imagen de producción, en tres etapas:
#   1. compila el frontend React,
#   2. lo mete dentro de los recursos estáticos del backend y arma el jar,
#   3. lo corre sobre un JRE pelado, sin toolchain ni fuentes.
#
# Resultado: TODO en una sola URL — API REST, MCP, frontend, cartelera, demo y widget.
# Los tests no corren acá (los corre el desarrollador o el CI): un build de imagen tiene que
# ser reproducible y rápido en un VPS chico.

# --- 1. Frontend ---
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- 2. Backend ---
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
# El build de Vite se sirve desde el jar, junto a las páginas estáticas que ya viven ahí.
COPY --from=frontend /frontend/dist src/main/resources/static/
RUN ./gradlew --no-daemon bootJar -x test

# --- 3. Runtime ---
FROM eclipse-temurin:25-jre
# curl para el healthcheck del compose; usuario sin privilegios porque es un servicio público.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home bondi
USER bondi
WORKDIR /app
COPY --from=backend /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
