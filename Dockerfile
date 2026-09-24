# =============================================================================
#  Un solo Dockerfile para los cuatro servicios de Spring.
# =============================================================================
#  Los cuatro se construyen igual y solo cambian en qué modulo empaquetan, asi
#  que en vez de cuatro archivos casi identicos hay uno con un argumento:
#
#      docker build --build-arg MODULO=ms-debt -t tbridge/ms-debt .
#
#  El compose lo hace por los cuatro (perfil "app").
#
#  Por que dos etapas: la primera necesita el JDK y Maven —unos 900 MB— y la
#  segunda solo necesita ejecutar el jar. La imagen que queda lleva el JRE y
#  nada mas, y el codigo fuente no viaja a produccion.
#
#  La etapa de construccion no depende de MODULO a proposito: asi Docker la
#  reutiliza tal cual para los cuatro servicios en vez de compilar el proyecto
#  cuatro veces.
# =============================================================================

FROM maven:3.9-eclipse-temurin-25 AS construccion
WORKDIR /obra

COPY pom.xml .
COPY common      common
COPY gateway     gateway
COPY ms-auth     ms-auth
COPY ms-debt     ms-debt
COPY ms-payments ms-payments

#  Sin pruebas: de eso se encarga la integracion continua, que las corre
#  contra MySQL de verdad. Una imagen se construye para desplegar, no para
#  descubrir que algo esta roto.
RUN mvn -B -q package -DskipTests


FROM eclipse-temurin:25-jre
ARG MODULO

#  curl es para el healthcheck del compose, que corre dentro del contenedor.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

#  Nadie corre como root sin necesitarlo: si alguien se escapa del proceso,
#  se encuentra con un usuario sin permisos sobre nada.
RUN useradd --system --create-home --uid 10001 tbridge
WORKDIR /app
COPY --from=construccion /obra/${MODULO}/target/*.jar app.jar
USER tbridge

#  Con el contenedor puesto, la JVM lee el limite de memoria del contenedor y
#  no el de la maquina: sin esto pide memoria que no tiene y la matan.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
