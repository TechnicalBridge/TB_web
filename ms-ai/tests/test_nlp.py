"""
Pruebas del motor local. Solo usan la biblioteca estandar:
    cd ms-ai && python -m unittest discover tests
"""

import unittest

from app.nlp import dinero, local_reply, sentimiento, totales

# Tal como las entrega ms-debt (GET /api/debts).
DEUDAS = [
    {"acreedor": "Patrimonio Inmuebles", "concepto": "Arriendo mensual", "moneda": "CLP",
     "montoOriginal": 1040000, "saldo": 1040000, "estado": "open"},
    {"acreedor": "Patrimonio Inmuebles", "concepto": "Arriendo oficina", "moneda": "UF",
     "montoOriginal": 115.5, "saldo": 96.25, "estado": "repacted"},
    {"acreedor": "Patrimonio Inmuebles", "concepto": "Arriendo mensual", "moneda": "CLP",
     "montoOriginal": 410000, "saldo": 0, "estado": "paid"},
]


class LosMontos(unittest.TestCase):

    def test_pesos_y_uf_se_escriben_como_en_chile(self):
        self.assertEqual(dinero(1040000), "$1.040.000")
        self.assertEqual(dinero(96.25, "UF"), "UF 96,25")
        self.assertEqual(dinero(1234.5, "UF"), "UF 1.234,50")

    def test_pesos_y_uf_no_se_suman(self):
        self.assertEqual(totales(DEUDAS), "$1.040.000 y UF 96,25")

    def test_antes_decia_que_se_debian_cero_pesos(self):
        """Leia remainingAmount, que ms-debt ya no manda: todo sumaba $0."""
        respuesta = local_reply("cuánto debo?", DEUDAS)
        self.assertIn("$1.040.000", respuesta)
        self.assertIn("UF 96,25", respuesta)
        self.assertIn("2 deuda(s) vigente(s)", respuesta)
        self.assertIn("1 ya está(n) pagada(s)", respuesta)


class ElSentimiento(unittest.TestCase):

    def test_frustracion(self):
        self.assertEqual(sentimiento("estoy harto, esto es un abuso")["etiqueta"], "frustracion")

    def test_desconfianza_pesa_mas_que_la_cortesia(self):
        self.assertEqual(sentimiento("gracias, pero esto es una estafa?")["etiqueta"], "desconfianza")

    def test_sin_tildes_ni_mayusculas(self):
        self.assertEqual(sentimiento("NO TENGO PLATA")["etiqueta"], "frustracion")

    def test_neutral(self):
        self.assertEqual(sentimiento("cuánto debo")["etiqueta"], "neutral")


class ElTono(unittest.TestCase):

    def test_quien_desconfia_recibe_como_verificar(self):
        respuesta = local_reply("¿esto es una estafa? cómo tienen mis datos", DEUDAS)
        self.assertIn("nunca te manda un enlace", respuesta)
        self.assertIn("Patrimonio Inmuebles", respuesta)
        self.assertNotIn("$", respuesta)

    def test_quien_no_puede_pagar_recibe_las_cuotas(self):
        respuesta = local_reply("no tengo plata para pagar", DEUDAS)
        self.assertTrue(respuesta.startswith("Entiendo que es una situación difícil."))
        self.assertIn("sin interés", respuesta)

    def test_el_certificado_solo_para_lo_pagado(self):
        self.assertIn("1 deuda(s) pagada(s)", local_reply("quiero el certificado", DEUDAS))
        self.assertIn("se emite cuando", local_reply("certificado", DEUDAS[:1]))


if __name__ == "__main__":
    unittest.main()
