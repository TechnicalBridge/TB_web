# syntax=docker/dockerfile:1
# =============================================================================
#  ms-debt — la imagen del servicio de deudas.
# =============================================================================
#  Se construye desde la raiz de TB_web, porque necesita el pom padre y common:
#
#      docker build -f ms-debt/Dockerfile -t tbridge/ms-debt .
#
#  El compose lo hace solo (perfil "app").
#
#  Dos etapas: la primera trae el JDK y Maven —unos 900 MB— y compila; la
#  segunda solo ejecuta el jar. La imagen final lleva el JRE y nada mas: ni
#  el codigo fuente ni las herramientas de compilacion viajan a produccion.
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS construccion
WORKDIR /obra

#  Maven lee todos los modulos que declara el pom padre aunque se compile uno
#  solo, asi que hacen falta sus pom. El codigo, solo el de common y el propio.
COPY pom.xml .
COPY common/pom.xml      common/pom.xml
COPY gateway/pom.xml     gateway/pom.xml
COPY ms-auth/pom.xml     ms-auth/pom.xml
COPY ms-debt/pom.xml     ms-debt/pom.xml
COPY ms-payments/pom.xml ms-payments/pom.xml
COPY common/src  common/src
COPY ms-debt/src ms-debt/src

#  -pl ms-debt -am: este servicio y lo que necesita (common), nada mas.
#  El cache de ~/.m2 sobrevive entre construcciones: las dependencias se bajan
#  una vez, no en cada cambio de codigo. Sin pruebas: las corre la integracion
#  continua; una imagen se construye para desplegar.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ms-debt -am package -DskipTests


FROM eclipse-temurin:25-jre

#  curl es para el healthcheck, que corre dentro del contenedor.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

#  Nadie corre como root sin necesitarlo: si alguien se escapa del proceso, se
#  encuentra con un usuario sin permisos sobre nada.
RUN useradd --system --create-home --uid 10001 tbridge
WORKDIR /app
COPY --from=construccion /obra/ms-debt/target/*.jar app.jar
USER tbridge

#  La JVM toma el limite de memoria del contenedor, no el de la maquina: sin
#  esto pide memoria que no tiene y Docker la mata.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8083

HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD curl -fs http://127.0.0.1:8083/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
