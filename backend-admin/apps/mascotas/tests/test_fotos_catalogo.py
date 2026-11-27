import json
from io import BytesIO
from tempfile import TemporaryDirectory
from unittest.mock import patch
from urllib.error import HTTPError

from django.contrib.auth import get_user_model
from django.contrib.messages.storage.cookie import CookieStorage
from django.db import IntegrityError
from django.http import Http404
from django.test import RequestFactory, TransactionTestCase, override_settings
from rest_framework.test import APIClient

from apps.albergues.models import Albergue
from apps.albergues.panel_views import mascota_editar
from apps.mascotas.fotos_catalogo import RUTA_REMOTA
from apps.mascotas.models import Mascota, PedidoAfiche
from apps.mascotas.serializers import MascotaCreateUpdateSerializer
from .test_api import foto_prueba


@override_settings(SUPABASE_URL="https://catalogo.example", SUPABASE_SECRET_KEY="sb_secret_prueba")
class FotosCatalogoTest(TransactionTestCase):
    objetos_url = "https://catalogo.example/storage/v1/object"
    bucket_url = f"{objetos_url}/petly-catalogo"
    publica_url = f"{objetos_url}/public/petly-catalogo"
    api_url = "/api/mascotas/"

    def setUp(self):
        media = self.enterContext(TemporaryDirectory())
        self.enterContext(override_settings(MEDIA_ROOT=media))
        self.objetos = {}
        self.http = self.enterContext(patch("apps.mascotas.fotos_catalogo.build_opener")).return_value.open
        self.http.side_effect = self.supabase
        self.fallo_subida = None
        self.user = get_user_model().objects.create_user(username="albergue", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(usuario=self.user, nombre="Albergue de prueba")
        self.api = APIClient()
        self.api.force_authenticate(self.user)
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, codigo="LOCAL", nombre="Luna", especie="GATO",
            sexo="HEMBRA", foto_principal=foto_prueba("historica.png"),
        )
        self.detalle = f"{self.api_url}{self.mascota.pk}/"

    def supabase(self, request, *, timeout):
        self.assertEqual(timeout, 10)
        cabeceras = {nombre.lower(): valor for nombre, valor in request.header_items()}
        if request.method == "POST":
            ruta = request.full_url.removeprefix(f"{self.bucket_url}/")
            self.assertRegex(ruta, RUTA_REMOTA)
            self.assertEqual(cabeceras["apikey"], "sb_secret_prueba")
            self.assertEqual(cabeceras["content-type"], "image/png")
            self.assertEqual(cabeceras["x-upsert"], "false")
            self.assertNotIn(ruta, self.objetos)
            if self.fallo_subida == 409:
                raise HTTPError(request.full_url, 409, "Ocupado", {}, None)
            self.objetos[ruta] = request.data
            if self.fallo_subida == "timeout":
                raise TimeoutError()
            clave = f"petly-catalogo/{ruta}" if not self.fallo_subida else "inesperada"
            return BytesIO(json.dumps({"Key": clave}).encode())
        elif request.method == "DELETE":
            self.assertEqual(request.full_url, self.bucket_url)
            self.assertEqual(cabeceras["apikey"], "sb_secret_prueba")
            for ruta in json.loads(request.data)["prefixes"]:
                self.objetos.pop(ruta, None)
        else:
            self.assertNotIn("apikey", cabeceras)
            ruta = request.full_url.removeprefix(f"{self.publica_url}/")
            if ruta not in self.objetos:
                raise HTTPError(request.full_url, 404, "Ausente", {}, None)
            return BytesIO(self.objetos[ruta])
        return BytesIO()

    def reemplazar(self, **datos):
        return self.api.patch(self.detalle, {"foto_principal": foto_prueba(), **datos}, format="multipart")

    def test_carga_nueva_devuelve_url_publica_y_permite_abrir_la_foto(self):
        foto = foto_prueba()
        contenido = foto.file.getvalue()
        response = self.api.post(self.api_url, {
            "nombre": "Nube", "especie": "GATO", "sexo": "HEMBRA", "foto_principal": foto,
        }, format="multipart")
        self.assertEqual(response.status_code, 201, response.data)
        mascota = Mascota.objects.get(pk=response.data["id"])
        self.assertRegex(mascota.foto_principal.name, RUTA_REMOTA)
        self.assertEqual(response.data["foto_principal"], f"{self.publica_url}/{mascota.foto_principal.name}")
        with mascota.foto_principal.open("rb") as archivo:
            self.assertEqual(archivo.read(), contenido)

    def test_reemplazo_conserva_archivo_local_y_limpia_remoto_sin_referencias(self):
        historica = self.mascota.foto_principal.name
        self.assertEqual(self.mascota.foto_principal.url, f"/media/{historica}")
        with self.mascota.foto_principal.open("rb") as archivo:
            self.assertEqual(archivo.read(), foto_prueba().file.getvalue())
        self.http.assert_not_called()
        self.assertEqual(self.reemplazar().status_code, 200)
        anterior, = self.objetos
        self.assertEqual(self.reemplazar().status_code, 200)
        nueva, = self.objetos
        self.assertNotEqual(nueva, anterior)
        self.assertTrue(self.mascota.foto_principal.storage.exists(historica))

    def test_reemplazo_conserva_foto_referenciada_por_ia_y_afiche(self):
        self.assertEqual(self.reemplazar().status_code, 200)
        original, = self.objetos
        self.assertEqual(self.reemplazar(foto_mejorada_confirmada="true").status_code, 200)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.foto_original_ia.name, original)
        mejorada = self.mascota.foto_principal.name
        PedidoAfiche.objects.create(mascota=self.mascota, albergue=self.albergue, foto_ruta=mejorada)
        self.assertEqual(self.reemplazar().status_code, 200)
        self.assertNotIn(original, self.objetos)
        self.assertIn(mejorada, self.objetos)

    def test_fallo_de_subida_ambiguo_conserva_anterior_y_limpia_el_objeto(self):
        anterior = self.mascota.foto_principal.name
        self.fallo_subida = "timeout"
        self.assertEqual(self.reemplazar(nombre="No guardado").status_code, 503)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.nombre, "Luna")
        self.assertEqual(self.mascota.foto_principal.name, anterior)
        self.assertEqual(self.objetos, {})

    def test_rechazo_por_nombre_ocupado_no_intenta_borrar_el_objeto_existente(self):
        self.fallo_subida = 409
        self.assertEqual(self.reemplazar().status_code, 503)
        self.assertEqual(self.http.call_count, 1)

    def test_respuesta_sin_la_clave_esperada_no_confirma_la_subida(self):
        anterior = self.mascota.foto_principal.name
        self.fallo_subida = "clave inesperada"
        self.assertEqual(self.reemplazar().status_code, 503)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.foto_principal.name, anterior)
        self.assertEqual(self.objetos, {})

    def test_fallo_de_guardado_revierte_la_ficha_y_limpia_la_subida(self):
        anterior = self.mascota.foto_principal.name
        actualizar = MascotaCreateUpdateSerializer.update

        def guardar_y_fallar(serializer, instance, data):
            actualizar(serializer, instance, data)
            raise IntegrityError("fallo simulado tras escribir")

        with patch.object(MascotaCreateUpdateSerializer, "update", guardar_y_fallar):
            with self.assertRaises(IntegrityError):
                self.reemplazar(nombre="No guardado")
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.nombre, "Luna")
        self.assertEqual(self.mascota.foto_principal.name, anterior)
        self.assertEqual(self.objetos, {})

    def test_ficha_ajena_y_archivos_invalidos_no_llegan_a_supabase(self):
        otro = get_user_model().objects.create_user(username="otro", rol="ALBERGUE")
        Albergue.objects.create(usuario=otro, nombre="Otro albergue")
        self.api.force_authenticate(otro)
        self.assertEqual(self.reemplazar().status_code, 404)
        request = RequestFactory().post("/", {"foto_principal": foto_prueba()})
        request.user = otro
        with self.assertRaises(Http404):
            mascota_editar(request, self.mascota.pk)

        self.api.force_authenticate(self.user)
        self.assertEqual(self.reemplazar(foto_principal=BytesIO(b"texto")).status_code, 400)
        with self.settings(PETLY_TEST_MAX_IMAGE_BYTES=64):
            self.assertEqual(self.reemplazar().status_code, 400)
        self.http.assert_not_called()

    def test_formulario_htmx_muestra_el_error_y_guarda_con_el_mismo_endpoint(self):
        factory = RequestFactory()
        request = factory.get("/")
        request.user = self.user
        self.assertContains(mascota_editar(request, self.mascota.pk), 'hx-encoding="multipart/form-data"')
        request = factory.post("/", {"foto_principal": BytesIO(b"texto")}, HTTP_HX_REQUEST="true")
        request.user = self.user
        response = mascota_editar(request, self.mascota.pk)
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.headers["HX-Retarget"], "#mascota-foto-error")
        self.http.assert_not_called()
        request = factory.post("/", {"foto_principal": foto_prueba()}, HTTP_HX_REQUEST="true")
        request.user = self.user
        request._messages = CookieStorage(request)
        response = mascota_editar(request, self.mascota.pk)
        self.mascota.refresh_from_db()
        self.assertIn(self.mascota.foto_principal.name, self.objetos)
        self.assertContains(response, self.mascota.foto_principal.url)
