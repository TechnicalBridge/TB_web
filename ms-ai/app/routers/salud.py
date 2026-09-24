"""GET /api/health: si el asistente esta arriba y si tiene un LLM."""

from __future__ import annotations

from fastapi import APIRouter

from app import config
from app.schemas.chat import Salud

router = APIRouter(tags=["Salud"])


@router.get("/api/health", response_model=Salud, summary="Estado del asistente")
def salud() -> Salud:
    return Salud(ok=True, llm=bool(config.XAI_API_KEY))
