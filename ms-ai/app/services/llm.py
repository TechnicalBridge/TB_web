"""
La respuesta con un LLM, si hay uno configurado.

Si no hay llave, o el LLM falla, devuelve None y el asistente responde con su
motor local de reglas (nlp.py). Nunca inventa montos: el contexto son las
deudas tal como las entrega ms-debt.
"""

from __future__ import annotations

from typing import Any

from app import config
from app.services.nlp import ESTADOS, dinero


def contexto(deudas: list[dict[str, Any]]) -> str:
    """Las deudas en montos legibles y sin mezclar monedas."""
    if not deudas:
        return "El deudor no tiene deudas visibles."
    lineas = []
    for d in deudas:
        moneda = d.get("moneda") or "CLP"
        lineas.append(
            f"- {d.get('acreedor')} ({d.get('concepto')}) | original {dinero(d.get('montoOriginal'), moneda)} | "
            f"saldo {dinero(d.get('saldo'), moneda)} | {ESTADOS.get(d.get('estado'), d.get('estado'))}"
        )
    return "Deudas (solo lectura; pesos y UF no se suman entre si):\n" + "\n".join(lineas)


def responder(historia: list[dict[str, Any]], deudas: list[dict[str, Any]], animo: str) -> str | None:
    if not config.XAI_API_KEY:
        return None
    sistema = (
        "Eres el asistente de Technical Bridge. Ayudas a deudores en Chile a entender cuánto deben, "
        "a quién, y cómo pagar o pagar en cuotas (3 a 24, sin interés). No inventes montos: usa solo "
        "el contexto. Nunca pidas contraseñas ni datos bancarios, y nunca mandes enlaces para entrar. "
        "Responde en español de Chile, breve. No ejecutes pagos: el botón Pagar abre la pasarela.\n"
        f"Ánimo detectado en el último mensaje: {animo}. Si es frustración, reconócela antes de "
        "responder; si es desconfianza, explica cómo verificar que esto es legítimo.\n\n"
        + contexto(deudas)
    )
    mensajes = [{"role": "system", "content": sistema}]
    for item in historia[-12:]:
        rol = item.get("role")
        contenido = item.get("content") or item.get("message")
        if rol in {"user", "assistant"} and contenido:
            mensajes.append({"role": rol, "content": str(contenido)})
    try:
        from openai import OpenAI

        cliente = OpenAI(api_key=config.XAI_API_KEY, base_url=config.XAI_BASE_URL)
        try:
            resp = cliente.responses.create(model=config.XAI_MODEL, input=mensajes)
            texto = getattr(resp, "output_text", None)
            if texto:
                return texto.strip()
        except Exception:
            chat = cliente.chat.completions.create(model=config.XAI_MODEL, messages=mensajes)
            return (chat.choices[0].message.content or "").strip()
    except Exception:
        return None
    return None
