from __future__ import annotations

import os
from pathlib import Path
from typing import Any

import httpx
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .nlp import local_reply

ROOT = Path(__file__).resolve().parents[2]
load_dotenv(ROOT / ".env")
load_dotenv(Path(__file__).resolve().parents[1] / ".env")

DEBT_URI = os.getenv("DEBT_URI", "http://127.0.0.1:8083").rstrip("/")
XAI_API_KEY = os.getenv("XAI_API_KEY", "").strip()
XAI_BASE_URL = os.getenv("XAI_BASE_URL", "https://api.x.ai/v1").rstrip("/")
XAI_MODEL = os.getenv("XAI_MODEL", "grok-4.5")

app = FastAPI(title="MS-AI", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class ChatIn(BaseModel):
    message: str = ""
    messages: list[dict[str, Any]] = Field(default_factory=list)


@app.get("/api/health")
def health() -> dict[str, Any]:
    return {"ok": True, "service": "ms-ai", "llm": bool(XAI_API_KEY)}


async def fetch_debts(authorization: str | None) -> list[dict[str, Any]]:
    if not authorization:
        return []
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            res = await client.get(
                f"{DEBT_URI}/api/debts",
                headers={"Authorization": authorization},
            )
            if res.status_code >= 400:
                return []
            data = res.json()
            return list(data.get("debts") or [])
    except Exception:
        return []


def context_block(debts: list[dict[str, Any]]) -> str:
    if not debts:
        return "El deudor no tiene deudas visibles."
    lines = []
    for d in debts:
        lines.append(
            f"- {d.get('creditorName')} | original {d.get('originalAmount')} | "
            f"saldo {d.get('remainingAmount')} {d.get('currency')} | estado {d.get('status')}"
        )
    return "Deudas (solo lectura):\n" + "\n".join(lines)


async def llm_reply(history: list[dict[str, Any]], debts: list[dict[str, Any]]) -> str | None:
    if not XAI_API_KEY:
        return None
    system = (
        "Eres el chatbot de Technical Bridge. Ayudas a deudores a entender saldo, cuotas y pagos. "
        "No inventes montos: usa solo el contexto. No pidas contraseñas. Responde en español, breve. "
        "No ejecutes pagos; indica que el botón Pagar abre la pasarela.\n\n"
        + context_block(debts)
    )
    messages = [{"role": "system", "content": system}]
    for item in history[-12:]:
        role = item.get("role")
        content = item.get("content") or item.get("message")
        if role in {"user", "assistant"} and content:
            messages.append({"role": role, "content": str(content)})
    try:
        from openai import OpenAI

        client = OpenAI(api_key=XAI_API_KEY, base_url=XAI_BASE_URL)
        try:
            resp = client.responses.create(model=XAI_MODEL, input=messages)
            text = getattr(resp, "output_text", None)
            if text:
                return text.strip()
        except Exception:
            chat = client.chat.completions.create(model=XAI_MODEL, messages=messages)
            return (chat.choices[0].message.content or "").strip()
    except Exception:
        return None
    return None


@app.post("/api/ai/chat")
async def chat(body: ChatIn, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    if not authorization:
        raise HTTPException(status_code=401, detail="No autorizado")
    debts = await fetch_debts(authorization)
    history = list(body.messages or [])
    if body.message:
        history.append({"role": "user", "content": body.message})
    last = ""
    for item in reversed(history):
        if item.get("role") == "user":
            last = str(item.get("content") or item.get("message") or "")
            break
    reply = await llm_reply(history, debts)
    source = "spacexai" if reply else "local-nlp"
    if not reply:
        reply = local_reply(last, debts)
    return {"reply": reply, "source": source, "debts": len(debts)}
