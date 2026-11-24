from io import BytesIO
from unittest.mock import patch

from django.core.files.storage import InMemoryStorage
from django.core.files.uploadedfile import SimpleUploadedFile
from django.urls import reverse
from django.utils import timezone
from PIL import Image
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, User
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.contratos.models import FotoCasoExito


class CasosExitoTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        cls.adoptante = User.objects.create_user(username="historias-adoptante", rol=User.Rol.ADOPTANTE)
        cls.responsable = User.objects.create_user(username="historias-albergue", rol=User.Rol.ALBERGUE)
        cls.otro_albergue = User.objects.create_user(username="historias-ajeno", rol=User.Rol.ALBERGUE)
        cls.albergue = Albergue.objects.create(usuario=cls.responsable, nombre="Huellitas", direccion="Lima")
        mascota = Mascota.objects.create(
            albergue=cls.albergue, nombre="Luna", codigo="HISTORIA-61", especie="GATO", sexo="HEMBRA",
        )
        cls.solicitud = SolicitudAdopcion.objects.create(
            adoptante=cls.adoptante, albergue=cls.albergue, mascota=mascota,
            estado=SolicitudAdopcion.Estado.ENTREGADA, fecha_entrega=timezone.localdate(),
        )

    def setUp(self):
        super().setUp()
        almacenamiento = patch.object(FotoCasoExito._meta.get_field("archivo"), "storage", InMemoryStorage())
        almacenamiento.start()
        self.addCleanup(almacenamiento.stop)
        self.client.force_authenticate(self.adoptante)

    def enviar_historia(self):
        with BytesIO() as buffer:
            Image.new("RGB", (12, 12)).save(buffer, format="PNG")
            contenido = buffer.getvalue()
        respuesta = self.client.post(reverse("caso-exito-list"), {
            "solicitud_id": self.solicitud.pk,
            "historia": "Luna ya se adaptó a su nuevo hogar.",
            "fotos": [
                SimpleUploadedFile("luna.png", contenido, content_type="image/png"),
                SimpleUploadedFile("casa.png", contenido, content_type="image/png"),
            ],
        }, format="multipart")
        self.assertEqual(respuesta.status_code, 201, respuesta.data)
        self.assertEqual(len(respuesta.data["fotos"]), 2)
        return respuesta.data

    def test_el_albergue_responsable_rechaza_en_json_y_el_adoptante_ve_el_motivo(self):
        disponibles = reverse("caso-exito-adopciones-disponibles")
        self.assertEqual([item["id"] for item in self.client.get(disponibles).data], [self.solicitud.pk])
        caso = self.enviar_historia()
        self.assertEqual(self.client.get(disponibles).data, [])
        pendientes = reverse("caso-exito-pendientes")
        revisar = reverse("caso-exito-revisar", args=[caso["id"]])
        foto_url = caso["fotos"][0]["descarga_url"]
        rechazo = {"decision": "RECHAZAR", "motivo": "  La foto muestra datos personales.  "}

        self.client.force_authenticate(self.otro_albergue)
        self.assertEqual(self.client.get(pendientes).data["results"], [])
        self.assertEqual(self.client.get(foto_url).status_code, 404)
        self.assertEqual(self.client.post(revisar, rechazo, format="json").status_code, 404)

        self.client.force_authenticate(self.responsable)
        self.assertEqual([item["id"] for item in self.client.get(pendientes).data["results"]], [caso["id"]])
        foto = self.client.get(foto_url)
        self.assertEqual(foto.status_code, 200)
        foto.close()
        sin_motivo = self.client.post(revisar, {"decision": "RECHAZAR", "motivo": " "}, format="json")
        self.assertEqual(sin_motivo.status_code, 400)
        self.assertIn("motivo", sin_motivo.data)
        respuesta = self.client.post(revisar, rechazo, format="json")
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        self.assertEqual(self.client.get(pendientes).data["results"], [])

        self.client.force_authenticate(self.adoptante)
        historia = self.client.get(reverse("caso-exito-list")).data["results"][0]
        self.assertEqual(historia["estado"], "RECHAZADO")
        self.assertEqual(historia["motivo_revision"], "La foto muestra datos personales.")
        self.assertIsNone(historia["publicado_en"])

    def test_la_historia_aprobada_se_puede_destacar_desde_el_panel(self):
        caso = self.enviar_historia()
        self.client.force_authenticate(self.responsable)
        aprobada = self.client.post(
            reverse("caso-exito-revisar", args=[caso["id"]]), {"decision": "APROBAR"}, format="json",
        )
        self.assertEqual(aprobada.status_code, 200, aprobada.data)
        self.assertEqual(aprobada.data["estado"], "APROBADO")
        self.assertIsNotNone(aprobada.data["publicado_en"])
        destacada = self.client.post(
            reverse("caso-exito-destacar", args=[caso["id"]]), {"destacado": True}, format="json",
        )
        self.assertEqual(destacada.status_code, 200, destacada.data)
        self.assertTrue(destacada.data["destacado"])
        publicadas = self.client.get(reverse("caso-exito-destacados"), {"albergue_id": self.albergue.pk})
        self.assertEqual([item["id"] for item in publicadas.data["results"]], [caso["id"]])
