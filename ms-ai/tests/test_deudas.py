"""
Como lee el asistente la respuesta de ms-debt. Solo biblioteca estandar:
    cd ms-ai && python -m unittest discover tests
"""

import unittest

from app.services.deudas import deudas_de


class LaRespuestaDeMsDebt(unittest.TestCase):

    def test_las_deudas_vienen_en_embedded(self):
        respuesta = {"_embedded": {"debts": [{"id": 3, "saldo": 96.25}]}, "_links": {}}
        self.assertEqual(deudas_de(respuesta), [{"id": 3, "saldo": 96.25}])

    def test_sin_deudas_no_viene_embedded_y_es_una_lista_vacia(self):
        self.assertEqual(deudas_de({"_links": {"self": {"href": "http://localhost:8080/api/debts"}}}), [])
        self.assertEqual(deudas_de(None), [])


if __name__ == "__main__":
    unittest.main()
