from datetime import date

from django.contrib.auth import get_user_model
from django.test import override_settings
from django.urls import reverse
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


@override_settings(CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}})
class ConversionAlbergueTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.responsable = usuarios.create_user(username="conversion-titular", rol="ALBERGUE")
        cls.otro_responsable = usuarios.create_user(username="conversion-ajeno", rol="ALBERGUE")
        cls.adoptante = usuarios.create_user(username="conversion-adoptante", rol="ADOPTANTE")
        cls.albergue = Albergue.objects.create(usuario=cls.responsable, nombre="Refugio", direccion="Lima")
        cls.otro_albergue = Albergue.objects.create(usuario=cls.otro_responsable, nombre="Otro refugio", direccion="Callao")
        cls.mascota = Mascota.objects.create(
            albergue=cls.albergue, codigo="CONV-69", nombre="Nube", especie="GATO", sexo="HEMBRA",
        )
        cls.mascota_ajena = Mascota.objects.create(
            albergue=cls.otro_albergue, codigo="CONV-69-A", nombre="Sol", especie="GATO", sexo="MACHO",
        )

    def setUp(self):
        self.client.force_authenticate(self.responsable)
        self.url = reverse("albergue-metrica-conversion")

    def solicitud(self, estado, mascota=None):
        mascota = mascota or self.mascota
        return SolicitudAdopcion.objects.create(
            albergue=mascota.albergue, mascota=mascota, estado=estado,
            fecha_entrega=date(2026, 1, 10) if estado == "ENTREGADA" else None,
        )

    def verificar_conversion(self, recibidas, aprobadas, tasa, filtros=None):
        respuesta = self.client.get(self.url, filtros)
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        self.assertEqual(respuesta["Cache-Control"], "private, no-store")
        self.assertEqual(respuesta.data, {
            "solicitudes_recibidas": recibidas,
            "solicitudes_aprobadas": aprobadas,
            "tasa_conversion": tasa,
        })

    def test_cuenta_una_vez_por_solicitud_incluidas_las_entregas_y_aisla_el_albergue(self):
        aprobada = self.solicitud("APROBADA")
        entregada = self.solicitud("ENTREGADA")
        self.solicitud("PENDIENTE")
        self.solicitud("RECHAZADA")
        self.solicitud("RECHAZADA")
        SolicitudEvento.objects.create(solicitud=aprobada, tipo="APROBADA", estado_nuevo="APROBADA")
        SolicitudEvento.objects.create(solicitud=entregada, tipo="APROBADA", estado_nuevo="APROBADA")
        SolicitudEvento.objects.create(solicitud=entregada, tipo="ENTREGADA", estado_nuevo="ENTREGADA")
        self.verificar_conversion(5, 2, 40)

        SolicitudEvento.objects.create(solicitud=aprobada, tipo="APROBADA", estado_nuevo="APROBADA")
        ajena = self.solicitud("APROBADA", self.mascota_ajena)
        SolicitudEvento.objects.create(solicitud=ajena, tipo="APROBADA", estado_nuevo="APROBADA")
        self.verificar_conversion(5, 2, 40, {"albergue": self.otro_albergue.pk})
        self.client.force_authenticate(self.otro_responsable)
        self.verificar_conversion(1, 1, 100)

    def test_reconoce_aprobaciones_anteriores_y_estados_antiguos_sin_evento(self):
        self.solicitud("APROBADA")
        self.solicitud("ENTREGADA")
        aprobacion_historica = self.solicitud("RECHAZADA")
        entrega_historica = self.solicitud("RECHAZADA")
        aprobacion_no_visible = self.solicitud("CANCELADA")
        SolicitudEvento.objects.create(solicitud=aprobacion_historica, tipo="REGISTRO_HISTORICO", estado_nuevo="APROBADA")
        SolicitudEvento.objects.create(solicitud=entrega_historica, tipo="REGISTRO_HISTORICO", estado_nuevo="ENTREGADA")
        SolicitudEvento.objects.create(solicitud=aprobacion_no_visible, tipo="APROBADA", visible_adoptante=False)
        self.verificar_conversion(5, 5, 100)

    def test_distingue_cero_aprobaciones_de_un_albergue_sin_solicitudes(self):
        self.verificar_conversion(0, 0, None)
        pendiente = self.solicitud("PENDIENTE")
        self.solicitud("RECHAZADA")
        self.solicitud("CANCELADA")
        SolicitudEvento.objects.create(solicitud=pendiente, tipo="REGISTRO_HISTORICO", estado_nuevo="PENDIENTE")
        self.verificar_conversion(3, 0, 0)

    def test_requiere_una_sesion_de_albergue_con_perfil(self):
        usuarios = get_user_model().objects
        admin = usuarios.create_user(username="conversion-admin", rol="ADMIN")
        sin_perfil = usuarios.create_user(username="conversion-sin-perfil", rol="ALBERGUE")
        for actor, estado in ((self.adoptante, 403), (admin, 403), (sin_perfil, 404), (None, 401)):
            with self.subTest(actor=actor):
                self.client.force_authenticate(actor)
                self.assertEqual(self.client.get(self.url).status_code, estado)
