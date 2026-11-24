import time
import uuid
from unittest.mock import patch

import jwt
from django.conf import settings
from django.db import DatabaseError, connection
from django.test import TestCase
from rest_framework_simplejwt.exceptions import InvalidToken

from apps.acceso.authentication import ComprobacionSesionNoDisponible, SpringBootJWTAuthentication
from apps.acceso.eventos_avisos import FlujoAvisos


class SesionSpringTest(TestCase):
    @classmethod
    def setUpTestData(cls):
        cls.sid = str(uuid.uuid4())
        with connection.cursor() as cursor:
            cursor.execute("""CREATE TABLE usuarios (
                id BIGINT PRIMARY KEY, token_version INT,
                cuenta_suspendida BOOLEAN DEFAULT FALSE, suspension_hasta DATETIME
            )""")
            cursor.execute("""CREATE TABLE sesiones_renovables (
                id VARCHAR(36), usuario_id BIGINT, token_version INT, revocada_en DATETIME
            )""")
            cursor.execute("INSERT INTO usuarios (id, token_version) VALUES (321, 4)")
            cursor.execute("INSERT INTO sesiones_renovables VALUES (%s, 321, 4, NULL)", [cls.sid])

    def validar(self, **claims):
        payload = {
            "sid": self.sid, "sub": "321", "version": 4, "type": "access", "rol": "ADOPTANTE",
            "iss": "petly-spring", "aud": ["petly-django"], "exp": int(time.time()) + 3600,
        }
        return SpringBootJWTAuthentication().get_validated_token(
            jwt.encode({**payload, **claims}, settings.SPRING_JWT_SECRET, algorithm="HS256")
        )

    def test_acepta_jwt_renovado_con_el_mismo_contrato(self):
        self.assertEqual(self.validar()["sub"], "321")

    def test_rechaza_revocacion_y_version_anterior(self):
        with self.assertRaises(InvalidToken):
            self.validar(version=3)
        with connection.cursor() as cursor:
            cursor.execute("UPDATE sesiones_renovables SET revocada_en = CURRENT_TIMESTAMP")
        with self.assertRaises(InvalidToken):
            self.validar()

    def test_error_transitorio_es_503(self):
        with patch("apps.acceso.authentication.connection.cursor", side_effect=DatabaseError):
            with self.assertRaises(ComprobacionSesionNoDisponible):
                self.validar()

    def test_rechaza_cuenta_suspendida_aunque_el_token_este_vigente(self):
        self.assertEqual(self.validar()["sub"], "321")
        with connection.cursor() as cursor:
            cursor.execute("UPDATE usuarios SET cuenta_suspendida = TRUE WHERE id = 321")
        with self.assertRaises(InvalidToken):
            self.validar()

    def test_stream_abierto_deja_de_emitir_despues_de_revocar(self):
        token = self.validar()
        flujo = FlujoAvisos(1, token["exp"], token)
        self.assertTrue(flujo.sesion_vigente())
        with connection.cursor() as cursor:
            cursor.execute("UPDATE sesiones_renovables SET revocada_en = CURRENT_TIMESTAMP")
        self.assertFalse(flujo.sesion_vigente())
