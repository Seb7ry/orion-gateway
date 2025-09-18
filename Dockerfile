# ===========================================
# ORION GATEWAY SERVICE - Dockerfile.dev (DEV/HOT-RELOAD)
# ===========================================
FROM eclipse-temurin:21-jdk

# utilidades
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# usuario no root (recomendado)
RUN useradd -ms /bin/bash orion
USER orion

WORKDIR /app

# Copiamos wrapper y config de maven para cachear deps
COPY --chown=orion:orion mvnw mvnw.cmd pom.xml ./
COPY --chown=orion:orion .mvn .mvn

# Resolver dependencias (rápido en subsiguientes builds)
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

# El código fuente se montará como volumen en runtime (compose)
# Exponemos el puerto interno del contenedor
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=docker

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Ejecuta en modo dev con hot-reload (DevTools)
CMD ["./mvnw", "spring-boot:run", "-Dspring-boot.run.profiles=docker"]