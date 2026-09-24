# syntax=docker/dockerfile:1
# =============================================================================
#  gateway — la imagen de la puerta de entrada a los servicios.
# =============================================================================
#  Se construye desde la raiz de TB_web, porque necesita el pom padre:
#
#      docker build -f gateway/Dockerfile -t tbridge/gateway .
#
#  Dos etapas: la primera compila con el JDK y Maven, la segunda solo ejecuta
#  el jar con el JRE.
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS construccion
WORKDIR /obra

#  Maven lee todos los modulos que declara el pom padre aunque se compile uno
#  solo. El gateway no usa common: solo viaja su propio codigo.
COPY pom.xml .
COPY common/pom.xml      common/pom.xml
COPY gateway/pom.xml     gateway/pom.xml
COPY ms-auth/pom.xml     ms-auth/pom.xml
COPY ms-debt/pom.xml     ms-debt/pom.xml
COPY ms-payments/pom.xml ms-payments/pom.xml
COPY gateway/src gateway/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl gateway -am package -DskipTests


FROM eclipse-temurin:25-jre

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --uid 10001 tbridge
WORKDIR /app
COPY --from=construccion /obra/gateway/target/*.jar app.jar
USER tbridge

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8082

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=12 \
  CMD curl -fs http://127.0.0.1:8082/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
