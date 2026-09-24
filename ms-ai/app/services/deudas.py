"""
Las deudas del deudor, pedidas a ms-debt con SU sesion.

ms-debt decide que puede ver. El asistente no tiene acceso propio a la base,
y por eso es de solo lectura por construccion, no por promesa.
"""

from __future__ import annotations

from typing import Any


def deudas_de(respuesta: dict[str, Any]) -> list[dict[str, Any]]:
    """
    La lista que viene en la respuesta de GET /api/debts.

    ms-debt responde en HAL: las deudas estan en _embedded.debts, y una lista
    vacia simplemente no trae _embedded.
    """
    return list(((respuesta or {}).get("_embedded") or {}).get("debts") or [])


async def deudas_del_deudor(authorization: str | None) -> list[dict[str, Any]]:
    """Si ms-debt no responde o dice que no, el asistente sigue: sin deudas que mostrar."""
    if not authorization:
        return []
    #  httpx y la configuracion (que usa python-dotenv) se importan aca y no
    #  arriba: asi deudas_de, y sus pruebas, corren solo con la biblioteca
    #  estandar, como en la integracion continua.
    import httpx

    from app import config

    try:
        async with httpx.AsyncClient(timeout=8.0) as cliente:
            res = await cliente.get(f"{config.DEBT_URI}/api/debts", headers={"Authorization": authorization})
            if res.status_code >= 400:
                return []
            return deudas_de(res.json())
    except Exception:
        return []
