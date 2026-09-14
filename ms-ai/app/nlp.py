from __future__ import annotations

import re
from typing import Any


def _clp(value: Any) -> str:
    try:
        n = int(float(value))
    except (TypeError, ValueError):
        return "$0"
    return f"${n:,.0f}".replace(",", ".")


def local_reply(message: str, debts: list[dict[str, Any]]) -> str:
    text = (message or "").strip().lower()
    remaining = sum(float(d.get("remainingAmount") or 0) for d in debts)
    active = [d for d in debts if d.get("status") != "PAGADA"]
    paid = [d for d in debts if d.get("status") == "PAGADA"]

    if re.search(r"\b(hola|buenas|hey|saludos)\b", text):
        return (
            "Hola, soy el asistente de Technical Bridge. Puedo informarte tu saldo, "
            "cuotas y cómo repactar o pagar. ¿Qué necesitas?"
        )
    if re.search(r"certificad", text):
        if remaining <= 0 and debts:
            return "Tu saldo está en cero. En Mis deudas puedes descargar el Certificado de Deuda Cero en PDF."
        return "El certificado PDF se emite cuando el saldo de esa deuda llega a cero."
    if re.search(r"repact|cuot|mes(es)?|plazo", text):
        return (
            "En el simulador eliges entre 3 y 24 meses. Recalculamos fechas y montos de cada cuota "
            "sobre el saldo pendiente, sin cambiar el total. Luego confirmas el plan."
        )
    if re.search(r"pagar|pasarela|khipu|webpay|mercado", text):
        return (
            "Desde cada deuda puedes pagar una cuota o el saldo. Abrimos Mercado Pago, Khipu o Webpay "
            "y, al confirmar, MS-Payments avisa a MS-Debt de forma asíncrona."
        )
    if re.search(r"saldo|deuda|cuánto|cuanto|debo|cartera", text):
        if not debts:
            return "No encuentro deudas a tu nombre. Si tu acreedor acaba de cargar un CSV, recarga Mis deudas."
        lines = [
            f"Tienes {len(active)} deuda(s) activa(s) y un saldo total de {_clp(remaining)}."
        ]
        for d in active[:5]:
            lines.append(
                f"- {d.get('creditorName')}: {_clp(d.get('remainingAmount'))} · {d.get('status')}"
            )
        if paid:
            lines.append(f"Además hay {len(paid)} obligación(es) ya pagada(s).")
        return "\n".join(lines)
    if re.search(r"gracias", text):
        return "Con gusto. Si cambia tu saldo, pregúntame de nuevo; leo la cartera en modo solo lectura."
    if remaining > 0:
        return (
            f"Saldo vigente: {_clp(remaining)} en {len(active)} deuda(s). "
            "Puedo hablar de saldo, cuotas, pago o certificado."
        )
    if debts:
        return "No registras saldo pendiente. Puedes emitir el Certificado de Deuda Cero."
    return "Estoy en modo consulta. Pregúntame por tu saldo, una repactación o cómo pagar."
