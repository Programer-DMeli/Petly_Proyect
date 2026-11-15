from concurrent.futures import ThreadPoolExecutor
from datetime import timedelta
from io import BytesIO
from tempfile import TemporaryDirectory
from threading import Barrier

from django.contrib.auth import get_user_model
from django.core.files.base import ContentFile
from django.core.files.uploadedfile import SimpleUploadedFile
from django.db import connections
from django.test import TestCase, TransactionTestCase, override_settings, skipUnlessDBFeature
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APIClient

from apps.albergues.models import Albergue
from apps.mascotas.afiches import MAX_INTENTOS
from apps.mascotas.models import Mascota, PedidoAfiche

TOKEN_TRABAJADOR = {"HTTP_X_AFICHE_WORKER_TOKEN": "solo-pruebas"}
Estado = PedidoAfiche.Estado


class DatosAfiche:
    def setUp(self):
        super().setUp()
        media = TemporaryDirectory()
        self.addCleanup(media.cleanup)
        configuracion = override_settings(
            MEDIA_ROOT=media.name, PRIVATE_FILES_ROOT=media.name, AFICHE_WORKER_TOKEN="solo-pruebas",
        )
        configuracion.enable()
        self.addCleanup(configuracion.disable)
        self.usuario = get_user_model().objects.create_user(username="albergue-afiche", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(
            usuario=self.usuario, nombre="Adopta", telefono="+51 999 000 111",
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre="Lazy", codigo="AFICHE-1", especie="PERRO", sexo="POR_EVAL",
        )
        self.guardar_foto("white")
        self.crear_url = reverse("afiche-crear", args=[self.mascota.pk])
        self.tomar_url = reverse("afiche-tomar")
        self.cliente = self.cliente_albergue(self.usuario)

    def guardar_foto(self, color):
        imagen = BytesIO()
        Image.new("RGB", (12, 12), color).save(imagen, "PNG")
        self.mascota.foto_principal.save("lazy.png", ContentFile(imagen.getvalue()))

    @staticmethod
    def cliente_albergue(usuario):
        cliente = APIClient()
        cliente.force_authenticate(user=usuario)
        return cliente

    def entregar(self, pedido, estado, fondo=None, **datos_extra):
        datos = {"reserva": pedido["reserva"], "estado": estado, **datos_extra}
        if estado == Estado.COMPLETADO:
            if fondo is None:
                imagen = BytesIO()
                Image.new("RGB", (80, 100), "white").save(imagen, "PNG")
                fondo = imagen.getvalue()
            datos["fondo"] = SimpleUploadedFile("fondo.png", fondo, content_type="image/png")
        return APIClient().post(
            reverse("afiche-entregar", args=[pedido["id"]]), datos, format="multipart", **TOKEN_TRABAJADOR,
        )


class PedidoAficheTest(DatosAfiche, TestCase):
    def test_deduplica_por_contenido_y_admite_otra_foto(self):
        primero = self.cliente.post(self.crear_url)
        self.assertEqual(primero.status_code, 201)
        self.guardar_foto("white")
        repetido = self.cliente.post(self.crear_url)
        self.assertEqual(repetido.status_code, 200)
        self.assertEqual(repetido.data["id"], primero.data["id"])
        self.guardar_foto("black")
        nuevo = self.cliente.post(self.crear_url)
        self.assertEqual(nuevo.status_code, 201)
        self.assertNotEqual(nuevo.data["foto_version"], primero.data["foto_version"])
        self.assertEqual(PedidoAfiche.objects.count(), 2)

    def test_albergue_ajeno_no_crea_ni_consulta(self):
        pedido = self.cliente.post(self.crear_url).data
        consulta = reverse("afiche-consultar", args=[pedido["id"]])
        self.assertEqual(self.cliente.get(consulta).data, pedido)
        otro = get_user_model().objects.create_user(username="otro-afiche", rol="ALBERGUE")
        Albergue.objects.create(usuario=otro, nombre="Otro albergue", email="otro@example.org")
        ajeno = self.cliente_albergue(otro)
        self.assertEqual(ajeno.post(self.crear_url).status_code, 404)
        self.assertEqual(ajeno.get(consulta).status_code, 404)

    def test_requiere_nombre_contacto_publico_y_foto_disponible(self):
        self.albergue.nombre = "  "
        self.albergue.save()
        self.assertIn("albergue", self.cliente.post(self.crear_url).data)
        self.albergue.nombre = "Adopta"
        self.albergue.telefono = "inválido"
        self.albergue.responsable_email = "privado@example.org"
        self.albergue.save()
        self.assertIn("contacto", self.cliente.post(self.crear_url).data)
        self.albergue.email = "publico@example.org"
        self.albergue.save()
        self.mascota.foto_principal.save("invalida.png", ContentFile(b"no es una foto"))
        self.assertIn("foto_principal", self.cliente.post(self.crear_url).data)
        self.mascota.foto_principal.storage.delete(self.mascota.foto_principal.name)
        self.assertIn("foto_principal", self.cliente.post(self.crear_url).data)
        self.mascota.foto_principal = None
        self.mascota.save(update_fields=["foto_principal"])
        self.assertIn("foto_principal", self.cliente.post(self.crear_url).data)
        self.assertFalse(PedidoAfiche.objects.exists())

    def test_api_interna_exige_token(self):
        self.assertEqual(self.cliente.post(self.tomar_url).status_code, 401)
        self.assertEqual(self.cliente.post(reverse("afiche-entregar", args=[1])).status_code, 401)
        with override_settings(AFICHE_WORKER_TOKEN=""):
            self.assertEqual(APIClient().post(self.tomar_url, **TOKEN_TRABAJADOR).status_code, 503)

    def test_reserva_expirada_no_entrega_ni_sobrescribe(self):
        creado = self.cliente.post(self.crear_url).data
        trabajador = APIClient()
        primero = trabajador.post(self.tomar_url, **TOKEN_TRABAJADOR).data
        self.assertEqual(primero["id"], creado["id"])
        self.assertEqual(primero["intentos"], 1)
        self.assertIn("/mascotas/", primero["foto_url"])
        self.assertEqual(trabajador.post(self.tomar_url, **TOKEN_TRABAJADOR).status_code, 204)
        self.assertEqual(self.cliente.post(self.crear_url).data["id"], creado["id"])
        PedidoAfiche.objects.filter(pk=creado["id"]).update(
            reserva_vence_en=timezone.now() - timedelta(seconds=1),
        )
        self.assertEqual(self.entregar(primero, Estado.COMPLETADO).status_code, 409)
        segundo = trabajador.post(self.tomar_url, **TOKEN_TRABAJADOR).data
        self.assertEqual(segundo["intentos"], 2)
        self.assertNotEqual(segundo["ruta_destino"], primero["ruta_destino"])
        self.assertEqual(self.entregar(primero, Estado.COMPLETADO).status_code, 409)
        entrega = self.entregar(segundo, Estado.COMPLETADO)
        self.assertEqual(entrega.status_code, 200)
        self.assertEqual(entrega.data["ruta_resultado"], segundo["ruta_destino"])
        self.assertEqual(self.entregar(primero, Estado.FALLIDO).status_code, 409)
        nuevo = self.cliente.post(self.crear_url)
        self.assertEqual(nuevo.status_code, 201)
        self.assertNotEqual(nuevo.data["id"], creado["id"])

    def test_entrega_valida_png_y_solo_owner_puede_descargar(self):
        self.cliente.post(self.crear_url)
        tomado = APIClient().post(self.tomar_url, **TOKEN_TRABAJADOR).data
        self.assertEqual(self.entregar(tomado, Estado.COMPLETADO, b"invalido").status_code, 400)
        entrega = self.entregar(tomado, Estado.COMPLETADO)
        ruta = entrega.data["fondo_url"]
        fondo = self.cliente.get(ruta)
        self.assertEqual(fondo.status_code, 200)
        self.assertEqual(fondo["Content-Type"], "image/png")
        self.assertTrue(b"".join(fondo.streaming_content).startswith(b"\x89PNG"))
        ajeno = get_user_model().objects.create_user(username="ajeno-fondo", rol="ALBERGUE")
        self.assertEqual(self.cliente_albergue(ajeno).get(ruta).status_code, 404)
        self.assertEqual(entrega.data["datos_afiche"]["telefono"], "+51 999 000 111")

    def test_error_identificable_puede_terminar_el_pedido(self):
        self.cliente.post(self.crear_url)
        tomado = APIClient().post(self.tomar_url, **TOKEN_TRABAJADOR).data
        entrega = self.entregar(tomado, Estado.FALLIDO, reintentar="false", error_codigo="SESION")
        self.assertEqual(entrega.data["estado"], Estado.FALLIDO)
        self.assertEqual(entrega.data["error_codigo"], "SESION")

    def test_reintentos_y_agotamiento_por_vencimiento(self):
        creado = self.cliente.post(self.crear_url).data
        trabajador = APIClient()
        for intento in range(1, MAX_INTENTOS + 1):
            tomado = trabajador.post(self.tomar_url, **TOKEN_TRABAJADOR).data
            self.assertEqual(tomado["intentos"], intento)
            if intento < MAX_INTENTOS:
                self.assertEqual(self.entregar(tomado, Estado.FALLIDO, reintentar="true").data["estado"], Estado.PENDIENTE)
        PedidoAfiche.objects.filter(pk=creado["id"]).update(
            reserva_vence_en=timezone.now() - timedelta(seconds=1),
        )
        self.assertEqual(trabajador.post(self.tomar_url, **TOKEN_TRABAJADOR).status_code, 204)
        self.assertEqual(PedidoAfiche.objects.get(pk=creado["id"]).estado, Estado.FALLIDO)


@skipUnlessDBFeature("has_select_for_update")
class PedidoAficheConcurrenteTest(DatosAfiche, TransactionTestCase):
    def en_paralelo(self, operacion):
        inicio = Barrier(2, timeout=10)

        def ejecutar():
            try:
                inicio.wait()
                respuesta = operacion()
                return respuesta.status_code, respuesta.data
            finally:
                connections.close_all()

        with ThreadPoolExecutor(max_workers=2) as pool:
            futuros = [pool.submit(ejecutar) for _ in range(2)]
            return [futuro.result(timeout=15) for futuro in futuros]

    def test_dos_clics_crean_un_pedido(self):
        respuestas = self.en_paralelo(lambda: self.cliente_albergue(self.usuario).post(self.crear_url))
        self.assertEqual(sorted(codigo for codigo, _ in respuestas), [200, 201])
        self.assertEqual(respuestas[0][1]["id"], respuestas[1][1]["id"])
        self.assertEqual(PedidoAfiche.objects.count(), 1)

    def test_dos_trabajadores_no_toman_el_mismo_pedido(self):
        creado = self.cliente.post(self.crear_url).data
        respuestas = self.en_paralelo(lambda: APIClient().post(self.tomar_url, **TOKEN_TRABAJADOR))
        self.assertEqual(sorted(codigo for codigo, _ in respuestas), [200, 204])
        tomado = next(datos for codigo, datos in respuestas if codigo == 200)
        self.assertEqual(tomado["id"], creado["id"])
        self.assertEqual(PedidoAfiche.objects.get(pk=creado["id"]).intentos, 1)
