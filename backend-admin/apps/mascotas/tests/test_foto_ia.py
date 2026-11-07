"""Pruebas del adaptador de edición de fotografía de US-27."""
import base64
import json
import os
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import patch
from urllib.error import HTTPError

from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import SimpleTestCase, override_settings
from PIL import Image

from apps.mascotas.foto_ia import (
    ErrorProveedorFoto,
    FotoIAError,
    _validar_imagen,
    mejorar_foto,
)


def png_bytes(color="red"):
    image = BytesIO()
    Image.new("RGB", (12, 8), color=color).save(image, format="PNG")
    return image.getvalue()


class ProveedorFotoTest(SimpleTestCase):
    def mascota_con_foto(self, content=None, filename="original.png"):
        photo = SimpleUploadedFile(
            filename, content or png_bytes(), content_type="image/png"
        )
        return SimpleNamespace(nombre="Luna", foto_principal=photo)

    def test_acepta_jpeg_y_png_validos_y_devuelve_dimensiones(self):
        content = png_bytes()

        self.assertEqual(_validar_imagen(content, "image/png"), (12, 8))

        jpeg = BytesIO()
        Image.new("RGB", (9, 7), color="blue").save(jpeg, format="JPEG")
        self.assertEqual(_validar_imagen(jpeg.getvalue(), "image/jpeg"), (9, 7))

    def test_rechaza_formato_no_permitido_y_archivo_corrupto(self):
        with self.assertRaisesRegex(ErrorProveedorFoto, "JPEG o PNG"):
            _validar_imagen(png_bytes(), "image/gif")
        with self.assertRaisesRegex(ErrorProveedorFoto, "inválida"):
            _validar_imagen(b"no es una imagen", "image/png")

    @override_settings(DEBUG=False)
    @patch.dict(os.environ, {
        "US27_IMAGE_API_URL": "https://images.example/edit",
        "US27_IMAGE_API_TOKEN": "test-token",
        "US27_IMAGE_API_TIMEOUT_SECONDS": "4",
    })
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_envia_imagen_y_devuelve_preview_validada(self, urlopen):
        improved = png_bytes("green")
        urlopen.return_value = BytesIO(json.dumps({
            "imageBase64": base64.b64encode(improved).decode("ascii"),
            "mimeType": "image/png",
        }).encode())

        result = mejorar_foto(self.mascota_con_foto())

        self.assertEqual(result["mime_type"], "image/png")
        self.assertEqual((result["ancho"], result["alto"]), (12, 8))
        self.assertTrue(result["data_url"].startswith("data:image/png;base64,"))
        request = urlopen.call_args.args[0]
        payload = json.loads(request.data.decode())
        self.assertEqual(request.get_header("Authorization"), "Bearer test-token")
        self.assertEqual(base64.b64decode(payload["image"]["base64"]), png_bytes())
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 4)

    @override_settings(DEBUG=False)
    @patch.dict(os.environ, {
        "US27_IMAGE_API_URL": "https://images.example/edit",
        "US27_IMAGE_API_TOKEN": "test-token",
    })
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_rechaza_salida_con_mime_o_contenido_invalido(self, urlopen):
        urlopen.return_value = BytesIO(json.dumps({
            "imageBase64": base64.b64encode(png_bytes()).decode("ascii"),
            "mimeType": "image/jpeg",
        }).encode())

        with self.assertRaisesRegex(ErrorProveedorFoto, "imagen inválida"):
            mejorar_foto(self.mascota_con_foto())

    @override_settings(DEBUG=False)
    @patch.dict(os.environ, {
        "US27_IMAGE_API_URL": "https://images.example/edit",
        "US27_IMAGE_API_TOKEN": "test-token",
    })
    @patch("apps.mascotas.foto_ia.urlopen", side_effect=OSError("offline"))
    def test_convierte_error_de_proveedor_en_error_controlado(self, urlopen):
        with self.assertRaisesRegex(ErrorProveedorFoto, "No fue posible conectar"):
            mejorar_foto(self.mascota_con_foto())
        urlopen.assert_called_once()

    @override_settings(DEBUG=False, GEMINI_API_KEY="")
    @patch.dict(os.environ, {"US27_IMAGE_API_URL": "", "US27_IMAGE_API_TOKEN": ""})
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_sin_proveedor_no_intenta_procesar(self, urlopen):
        from apps.mascotas.foto_ia import ProveedorFotoNoConfigurado

        with self.assertRaises(ProveedorFotoNoConfigurado):
            mejorar_foto(self.mascota_con_foto())
        urlopen.assert_not_called()

    @override_settings(GEMINI_API_KEY="test-gemini-key")
    @patch.dict(os.environ, {"US27_IMAGE_API_URL": "", "US27_IMAGE_API_TOKEN": "",
                             "GEMINI_IMAGE_MODEL": "gemini-2.5-flash-image"})
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_usa_gemini_existente_para_editar_sin_proveedor_externo(self, urlopen):
        improved = png_bytes("green")
        urlopen.return_value = BytesIO(json.dumps({"candidates": [{
            "finishReason": "STOP", "content": {"parts": [
                {"text": "Fotografía mejorada"},
                {"inlineData": {"mimeType": "image/png", "data": base64.b64encode(improved).decode()}},
            ]},
        }]}).encode())
        mascota = self.mascota_con_foto()
        result = mejorar_foto(mascota)
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent")
        self.assertEqual(request.get_header("X-goog-api-key"), "test-gemini-key")
        self.assertIsNone(request.get_header("Authorization"))
        body = json.loads(request.data)
        self.assertEqual(base64.b64decode(body["contents"][0]["parts"][1]["inlineData"]["data"]), png_bytes())
        self.assertEqual(body["generationConfig"]["responseModalities"], ["TEXT", "IMAGE"])
        self.assertEqual(base64.b64decode(result["base64"]), improved)
        self.assertEqual(mascota.foto_principal.name, "original.png")

    @override_settings(GEMINI_API_KEY="test-gemini-key")
    @patch.dict(os.environ, {"US27_IMAGE_API_URL": "", "US27_IMAGE_API_TOKEN": ""})
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_gemini_sin_imagen_o_bloqueada_no_finge_una_mejora(self, urlopen):
        for response in ({"candidates": []}, {"candidates": [{"finishReason": "SAFETY"}]},
                         {"candidates": [{"finishReason": "STOP", "content": {"parts": [{"text": "Sin imagen"}]}}]}):
            with self.subTest(response=response):
                urlopen.return_value = BytesIO(json.dumps(response).encode())
                with self.assertRaises(ErrorProveedorFoto):
                    mejorar_foto(self.mascota_con_foto())

    @override_settings(GEMINI_API_KEY="test-gemini-key")
    @patch.dict(os.environ, {"US27_IMAGE_API_URL": "", "US27_IMAGE_API_TOKEN": ""})
    @patch("apps.mascotas.foto_ia.urlopen", side_effect=HTTPError("https://example.test", 429, "quota", {}, None))
    def test_gemini_informa_falta_de_cuota_sin_exponer_respuesta_del_proveedor(self, urlopen):
        with self.assertRaisesRegex(ErrorProveedorFoto, "cuota disponible"):
            mejorar_foto(self.mascota_con_foto())

    @override_settings(DEBUG=False)
    @patch.dict(os.environ, {
        "US27_IMAGE_API_URL": "https://images.example/edit",
        "US27_IMAGE_API_TOKEN": "test-token",
    })
    @patch("apps.mascotas.foto_ia.urlopen")
    def test_rechaza_foto_original_invalida_antes_de_llamar_proveedor(self, urlopen):
        mascota = self.mascota_con_foto(b"contenido no valido")

        with self.assertRaisesRegex(FotoIAError, "JPEG o PNG válida"):
            mejorar_foto(mascota)
        urlopen.assert_not_called()
