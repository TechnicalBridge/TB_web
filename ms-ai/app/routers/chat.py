"""POST /api/ai/chat: una pregunta del deudor sobre sus deudas."""

from __future__ import annotations

from fastapi import APIRouter, Header, HTTPException

from app.schemas.chat import Pregunta, Respuesta
from app.services import llm
from app.services.deudas import deudas_del_deudor
from app.services.nlp import local_reply, sentimiento

router = APIRouter(prefix="/api/ai", tags=["Asistente"])


@router.post(
    "/chat",
    response_model=Respuesta,
    summary="Preguntarle al asistente",
    description=(
        "Responde sobre las deudas del deudor de la sesion: cuánto debe, a quién, cómo pagar en cuotas. "
        "Solo lee: no modifica ninguna deuda. Detecta frustración y desconfianza y ajusta el tono. "
        "Con un LLM configurado responde con él; si no, con reglas locales."
    ),
    responses={401: {"description": "Sin sesion"}},
)
async def chat(pregunta: Pregunta, authorization: str | None = Header(default=None)) -> Respuesta:
    if not authorization:
        raise HTTPException(status_code=401, detail="No autorizado")
    deudas = await deudas_del_deudor(authorization)

    historia = list(pregunta.messages or [])
    if pregunta.message:
        historia.append({"role": "user", "content": pregunta.message})
    ultimo = next((str(m.get("content") or m.get("message") or "")
                   for m in reversed(historia) if m.get("role") == "user"), "")

    animo = sentimiento(ultimo)
    respuesta = llm.responder(historia, deudas, animo["etiqueta"])
    fuente = "spacexai" if respuesta else "local-nlp"
    return Respuesta(
        reply=respuesta or local_reply(ultimo, deudas),
        source=fuente,
        debts=len(deudas),
        sentimiento=animo,
    )
