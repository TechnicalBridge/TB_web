# =============================================================================
#  El asistente del portal (FastAPI).
# =============================================================================
#  Es el unico servicio que no es de Spring. No tiene base de datos propia a
#  proposito: consulta las deudas a ms-debt con la sesion del deudor, asi que
#  no puede ver nada que el deudor no pueda ver.
# =============================================================================

#  3.13: la misma version con que el CI corre las pruebas. Una imagen con una
#  version distinta de la probada es una imagen sin probar.
FROM python:3.13-slim
WORKDIR /app

#  Las dependencias antes que el codigo: mientras requirements.txt no cambie,
#  Docker reutiliza esta capa y no vuelve a bajar nada.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app app

RUN useradd --system --create-home --uid 10001 tbridge
USER tbridge

#  0.0.0.0 y no 127.0.0.1: dentro del contenedor, escuchar solo en localhost
#  significa que nadie de afuera lo alcanza.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8085"]
