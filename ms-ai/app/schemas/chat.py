"""Lo que entra y sale del asistente."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class Pregunta(BaseModel):
    """El mensaje nuevo y la conversacion hasta ahora."""

    message: str = Field(default="", description="Lo que escribio el deudor", examples=["¿Cuánto debo?"])
    messages: list[dict[str, Any]] = Field(
        default_factory=list,
        description="La conversacion anterior: [{role: user|assistant, content}]",
    )


class Animo(BaseModel):
    etiqueta: str = Field(description="desconfianza, frustracion, positivo o neutral", examples=["frustracion"])
    intensidad: int = Field(description="Cuantas senales se encontraron", examples=[1])


class Respuesta(BaseModel):
    reply: str = Field(description="La respuesta para el deudor")
    source: str = Field(description="spacexai si respondio el LLM; local-nlp si las reglas", examples=["local-nlp"])
    debts: int = Field(description="Cuantas deudas vio el asistente", examples=[2])
    sentimiento: Animo


class Salud(BaseModel):
    ok: bool
    service: str = "ms-ai"
    llm: bool = Field(description="Si hay un LLM configurado")
