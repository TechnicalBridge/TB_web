# syntax=docker/dockerfile:1
# =============================================================================
#  gateway — la imagen de la puerta de entrada a los servicios.
# =============================================================================
#  Se construye desde la carpeta de este servicio, que no necesita nada
#  de los demas:
#
#      docker build -t tbridge/gateway gateway
#
#  Dos etapas: la primera compila con el JDK y Maven, la segunda solo ejecuta
#  el jar con el JRE.
# =============================================================================

FROM maven:3.9-eclipse-temurin-26 AS construccion
WORKDIR /obra

#  Solo este servicio: su pom y su codigo.
COPY pom.xml .
COPY src src

#  El cache de ~/.m2 sobrevive entre construcciones: las dependencias se bajan
#  una vez, no en cada cambio de codigo. Sin pruebas: las corre la integracion
#  continua; una imagen se construye para desplegar.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests


FROM eclipse-temurin:25-jre

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 10001 tbridge
WORKDIR /app
COPY --from=construccion /obra/target/*.jar app.jar
USER tbridge

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
  CMD curl -fs http://127.0.0.1:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
