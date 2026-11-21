from datetime import date

from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota, RegistroClinico


class AlertaClinicaTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.propietario = usuarios.create_user(username="refugio-58", rol="ALBERGUE")
        cls.otro_albergue = usuarios.create_user(username="otro-refugio-58", rol="ALBERGUE")
        cls.titular = usuarios.create_user(username="titular-58", rol="ADOPTANTE")
        cls.otro_adoptante = usuarios.create_user(username="otro-adoptante-58", rol="ADOPTANTE")
        albergue = Albergue.objects.create(usuario=cls.propietario, nombre="Refugio", direccion="Lima")
        Albergue.objects.create(usuario=cls.otro_albergue, nombre="Otro refugio", direccion="Lima")
        cls.mascota = Mascota.objects.create(
            albergue=albergue, nombre="Nube", codigo="AL-058", especie="GATO", sexo="HEMBRA",
            vacunas_al_dia=True, necesidades_especiales="Dieta habitual.", estado_salud_general="Control al día.",
        )
        cls.otra_mascota = Mascota.objects.create(
            albergue=albergue, nombre="Luna", codigo="AL-059", especie="GATO", sexo="HEMBRA",
            alerta_clinica="Alerta de Luna.",
        )
        cls.mascota.registros_clinicos.create(tipo=RegistroClinico.Tipo.VACUNACION, fecha=date(2025, 10, 1))
        cls.solicitud = SolicitudAdopcion.objects.create(
            adoptante=cls.titular, mascota=cls.mascota, albergue=albergue,
            estado=SolicitudAdopcion.Estado.ENTREGADA, fecha_entrega=date(2026, 11, 10),
        )

    def setUp(self):
        self.ficha = reverse("mascota-detail", args=[self.mascota.pk])
        self.alerta = reverse("solicitud-alerta-clinica", args=[self.solicitud.pk])

    def test_el_albergue_registra_reabre_edita_y_retira_la_alerta(self):
        self.client.force_authenticate(self.propietario)
        inicial = self.client.get(self.ficha).data
        self.assertEqual(inicial["alerta_clinica"], "")
        for texto, esperado in (
            ("  Alergia registrada.\nEvitar el alimento indicado.  ", "Alergia registrada.\nEvitar el alimento indicado."),
            ("Cuidados actualizados.", "Cuidados actualizados."),
            ("", ""),
        ):
            with self.subTest(texto=texto):
                respuesta = self.client.patch(self.ficha, {"alerta_clinica": texto}, format="multipart")
                self.assertEqual(respuesta.status_code, status.HTTP_200_OK, respuesta.data)
                ficha = self.client.get(self.ficha).data
                self.assertEqual(ficha["alerta_clinica"], esperado)
                for campo in ("vacunas_al_dia", "necesidades_especiales", "estado_salud_general", "registros_clinicos"):
                    self.assertEqual(ficha[campo], inicial[campo])
                self.otra_mascota.refresh_from_db()
                self.assertEqual(self.otra_mascota.alerta_clinica, "Alerta de Luna.")

    def test_rechaza_entradas_invalidas_y_escritura_por_otro_albergue(self):
        self.client.force_authenticate(self.propietario)
        for valor in (None, ["Alerta"], "a" * 2001):
            with self.subTest(valor=valor):
                respuesta = self.client.patch(self.ficha, {"alerta_clinica": valor}, format="json")
                self.assertEqual(respuesta.status_code, status.HTTP_400_BAD_REQUEST)
                self.assertIn("alerta_clinica", respuesta.data)

        self.client.force_authenticate(self.otro_albergue)
        respuesta = self.client.patch(self.ficha, {"alerta_clinica": "Cambio ajeno"}, format="multipart")
        self.assertEqual(respuesta.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(self.client.get(self.ficha).status_code, status.HTTP_404_NOT_FOUND)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.alerta_clinica, "")

    def test_el_titular_recibe_solo_la_alerta_de_la_mascota_entregada(self):
        self.client.force_authenticate(self.titular)
        vacia = self.client.get(self.alerta)
        self.assertEqual(vacia.status_code, status.HTTP_200_OK)
        self.assertEqual(vacia.data, {"alerta_clinica": ""})

        Mascota.objects.filter(pk=self.mascota.pk).update(alerta_clinica="Alerta de Nube.")
        respuesta = self.client.get(self.alerta, {"mascota": self.otra_mascota.pk})
        self.assertEqual(respuesta.status_code, status.HTTP_200_OK)
        self.assertEqual(respuesta.data, {"alerta_clinica": "Alerta de Nube."})
        self.assertEqual(respuesta["Cache-Control"], "private, no-store")
        self.assertEqual(self.client.get(self.ficha).status_code, status.HTTP_404_NOT_FOUND)

    def test_el_acceso_privado_rechaza_terceros_y_solicitudes_sin_entrega(self):
        Mascota.objects.filter(pk=self.mascota.pk).update(alerta_clinica="Alerta privada.")
        for actor, codigo in (
            (self.otro_adoptante, status.HTTP_404_NOT_FOUND),
            (self.propietario, status.HTTP_403_FORBIDDEN),
            (None, status.HTTP_401_UNAUTHORIZED),
        ):
            with self.subTest(actor=actor):
                self.client.force_authenticate(actor)
                respuesta = self.client.get(self.alerta)
                self.assertEqual(respuesta.status_code, codigo)
                self.assertNotIn("alerta_clinica", respuesta.data)

        self.client.force_authenticate(self.titular)
        for estado in SolicitudAdopcion.Estado.values:
            if estado == SolicitudAdopcion.Estado.ENTREGADA:
                continue
            with self.subTest(estado=estado):
                SolicitudAdopcion.objects.filter(pk=self.solicitud.pk).update(estado=estado, fecha_entrega=None)
                respuesta = self.client.get(self.alerta)
                self.assertEqual(respuesta.status_code, status.HTTP_403_FORBIDDEN)
                self.assertNotIn("alerta_clinica", respuesta.data)
