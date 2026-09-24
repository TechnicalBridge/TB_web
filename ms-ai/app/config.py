"""
La configuracion del asistente, desde variables de entorno.

Un .env en la raiz de TB_web o en ms-ai, si lo hay; las variables de entorno
de verdad (Docker, la terminal) mandan sobre el.
"""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

_MS_AI = Path(__file__).resolve().parents[1]
load_dotenv(_MS_AI.parent / ".env")
load_dotenv(_MS_AI / ".env")

#  Donde pedir las deudas del deudor, con su propia sesion.
DEBT_URI = os.getenv("DEBT_URI", "http://127.0.0.1:8083").rstrip("/")

#  Opcional: un LLM compatible con la API de OpenAI (xAI). Sin llave, el
#  asistente responde con su motor local de reglas.
XAI_API_KEY = os.getenv("XAI_API_KEY", "").strip()
XAI_BASE_URL = os.getenv("XAI_BASE_URL", "https://api.x.ai/v1").rstrip("/")
XAI_MODEL = os.getenv("XAI_MODEL", "grok-4.5")
