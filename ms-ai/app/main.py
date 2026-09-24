"""
ms-ai: el asistente del portal.

Es el unico servicio que no es de Spring. No tiene base de datos propia:
consulta las deudas a ms-debt con la sesion del deudor, asi que no puede ver
nada que el deudor no pueda ver. Su documentacion esta en /docs, y el Swagger
del gateway la incluye.

Sin CORS: el navegador llega por el gateway, que es el que decide los origenes.
"""

from __future__ import annotations

from fastapi import FastAPI

from app.routers import chat, salud

app = FastAPI(
    title="ms-ai - Asistente",
    version="1.0.0",
    description=(
        "El asistente del portal del deudor: responde cuánto debe, a quién y cómo pagar, "
        "con el tono ajustado a su ánimo. Solo lee."
    ),
)
app.include_router(salud.router)
app.include_router(chat.router)
