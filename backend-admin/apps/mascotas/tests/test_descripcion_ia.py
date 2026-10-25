"""Pruebas del contrato REST del proveedor de visión (US-29)."""
import base64
import json
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import patch

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings

from apps.mascotas.descripcion_ia import (
    DescripcionIAError,
    ErrorProveedorDescripcion,
    ProveedorDescripcionNoConfigurado,
    generar_borrador,
)


class ProveedorDescripcionTest(SimpleTestCase):
    def mascota_con_foto(self, filename="mascota.png", content=b"png-falso"):
        photo = SimpleUploadedFile(filename, content, content_type="image/png")
        return SimpleNamespace(foto_principal=photo)

    @override_settings(
        PET_DESCRIPTION_API_URL="https://vision.example/api/draft",
        PET_DESCRIPTION_API_TOKEN="test-token",
        PET_DESCRIPTION_TIMEOUT_SECONDS=4,
        DEBUG=False,
    )
    @patch("apps.mascotas.descripcion_ia.urlopen")
    def test_envia_foto_y_recibe_borrador_editable(self, urlopen):
        urlopen.return_value = BytesIO(
            json.dumps({"description": "Pelaje blanco con manchas marrones visibles."}).encode()
        )

        draft = generar_borrador(self.mascota_con_foto())

        self.assertEqual(draft, "Pelaje blanco con manchas marrones visibles.")
        request = urlopen.call_args.args[0]
        payload = json.loads(request.data.decode())
        self.assertEqual(request.get_header("Authorization"), "Bearer test-token")
        self.assertEqual(payload["image"]["mimeType"], "image/png")
        self.assertEqual(base64.b64decode(payload["image"]["base64"]), b"png-falso")
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 4)

    @override_settings(
        PET_DESCRIPTION_API_URL="https://vision.example/api/draft",
        PET_DESCRIPTION_API_TOKEN="test-token",
        DEBUG=False,
    )
    @patch("apps.mascotas.descripcion_ia.urlopen")
    def test_rechaza_inferencias_de_raza_y_no_devuelve_el_texto(self, urlopen):
        urlopen.return_value = BytesIO(json.dumps({"description": "Parece de raza labrador."}).encode())

        with self.assertRaisesRegex(ErrorProveedorDescripcion, "inferencias"):
            generar_borrador(self.mascota_con_foto())

    @override_settings(
        PET_DESCRIPTION_API_URL="http://vision.example/api/draft",
        PET_DESCRIPTION_API_TOKEN="test-token",
        DEBUG=False,
    )
    def test_exige_https_fuera_de_debug(self):
        with self.assertRaises(ProveedorDescripcionNoConfigurado):
            generar_borrador(self.mascota_con_foto())

    @override_settings(PET_DESCRIPTION_API_URL="", PET_DESCRIPTION_API_TOKEN="")
    def test_sin_credenciales_no_intenta_llamar_al_proveedor(self):
        with self.assertRaises(ProveedorDescripcionNoConfigurado):
            generar_borrador(self.mascota_con_foto())

    @override_settings(
        PET_DESCRIPTION_API_URL="https://vision.example/api/draft",
        PET_DESCRIPTION_API_TOKEN="test-token",
        DEBUG=False,
    )
    def test_rechaza_mascota_sin_fotografia(self):
        with self.assertRaisesRegex(DescripcionIAError, "necesita una foto"):
            generar_borrador(SimpleNamespace(foto_principal=None))
