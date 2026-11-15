from django.db import transaction
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento, User
from apps.acceso.solicitudes import cambiar_estado_solicitud
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class NotificacionesTest(APITestCase):
    url = "/api/acceso/notificaciones/"

    def setUp(self):
        self.adoptante = User.objects.create_user(username="avisos", rol=User.Rol.ADOPTANTE)
        self.otro = User.objects.create_user(username="otro", rol=User.Rol.ADOPTANTE)
        self.staff = User.objects.create_user(username="refugio", rol=User.Rol.ALBERGUE)
        self.albergue = Albergue.objects.create(usuario=self.staff, nombre="Huellitas", direccion="Lima")
        self.mascota = Mascota.objects.create(
            albergue=self.albergue, nombre="Luna", codigo="AVISO-01",
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA,
        )
        self.solicitud = SolicitudAdopcion.objects.create(
            adoptante=self.adoptante, mascota=self.mascota, albergue=self.albergue,
        )
        self.client.force_authenticate(self.adoptante)

    def evento(self, **kwargs):
        return SolicitudEvento.objects.create(
            solicitud=self.solicitud, tipo=SolicitudEvento.Tipo.SOLICITUD_CREADA, **kwargs,
        )

    def test_transicion_genera_aviso_con_motivo_y_estado_actual(self):
        cambiar_estado_solicitud(self.solicitud.pk, "OBSERVADA", self.staff, "Falta confirmar el horario")
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["no_leidas"], 1)
        aviso = response.data["results"][0]
        self.assertEqual(aviso["tipo"], "OBSERVADA")
        self.assertEqual(aviso["descripcion"], "Falta confirmar el horario")
        self.assertEqual(aviso["estado_actual"], "OBSERVADA")
        self.assertEqual(aviso["mascota_nombre"], "Luna")
        self.assertEqual(aviso["albergue_nombre"], "Huellitas")
        self.assertEqual(aviso["solicitud_id"], self.solicitud.pk)

    def test_solo_titular_puede_listar_y_leer_incluso_si_otro_es_staff(self):
        evento = self.evento()
        ajeno = User.objects.create_user(username="otro-albergue", rol=User.Rol.ALBERGUE, is_staff=True)
        for user in [self.otro, ajeno]:
            self.client.force_authenticate(user)
            self.assertEqual(self.client.get(self.url).data["results"], [])
            self.assertEqual(self.client.post(f"{self.url}{evento.pk}/leer/").status_code, 404)
            self.client.post(self.url + "leer-todas/")
        evento.refresh_from_db()
        self.assertIsNone(evento.leido_en)

    def test_lectura_persistente_e_idempotente(self):
        evento = self.evento()
        url = f"{self.url}{evento.pk}/leer/"
        response = self.client.post(url)
        self.assertEqual(response.status_code, 200)
        self.assertIsNotNone(response.data["leido_en"])
        self.assertEqual(self.client.post(url).data["leido_en"], response.data["leido_en"])
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.get(self.url).data["no_leidas"], 0)

    def test_no_expone_eventos_privados_ni_registros_historicos(self):
        privado = self.evento(visible_adoptante=False)
        historico = SolicitudEvento.objects.create(solicitud=self.solicitud, tipo="REGISTRO_HISTORICO")
        self.assertEqual(self.client.get(self.url).data["results"], [])
        for evento in [privado, historico]:
            self.assertEqual(self.client.post(f"{self.url}{evento.pk}/leer/").status_code, 404)

    def test_paginacion_orden_y_lectura_de_todas_las_paginas(self):
        eventos = [self.evento() for _ in range(32)]
        first = self.client.get(self.url).data
        self.assertEqual(len(first["results"]), 30)
        self.assertEqual(first["no_leidas"], 32)
        self.assertEqual(first["results"][0]["id"], eventos[-1].pk)
        second = self.client.get(self.url, {"page": 2}).data
        self.assertEqual(len(second["results"]), 2)
        self.assertIsNone(second["next"])
        self.assertEqual(self.client.post(self.url + "leer-todas/").data["actualizadas"], 32)
        self.assertEqual(self.client.get(self.url).data["no_leidas"], 0)

    def test_rollback_no_deja_notificacion(self):
        try:
            with transaction.atomic():
                cambiar_estado_solicitud(self.solicitud.pk, "RECHAZADA", self.staff, "Sin disponibilidad")
                raise RuntimeError("rollback")
        except RuntimeError:
            pass
        self.assertEqual(self.client.get(self.url).data["results"], [])
        self.solicitud.refresh_from_db()
        self.assertEqual(self.solicitud.estado, "PENDIENTE")

    def test_requiere_autenticacion(self):
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(self.url).status_code, 401)

    def test_albergue_recibe_estados_y_mantiene_lectura_independiente(self):
        creada = self.evento()
        cambiar_estado_solicitud(self.solicitud.pk, "OBSERVADA", self.staff, "Confirma el horario")
        SolicitudEvento.objects.create(solicitud=self.solicitud, tipo="SEGUIMIENTO_PROGRAMADO")
        self.client.force_authenticate(self.staff)
        bandeja = self.client.get(self.url).data
        self.assertEqual(bandeja["no_leidas"], 2)
        self.assertEqual([aviso["tipo"] for aviso in bandeja["results"]], ["OBSERVADA", "SOLICITUD_CREADA"])
        self.assertEqual(bandeja["results"][1]["mensaje"], "Recibiste una nueva solicitud de adopción.")
        url = f"{self.url}{creada.pk}/leer/"
        leida = self.client.post(url).data["leido_en"]
        self.assertIsNotNone(leida)
        self.assertEqual(self.client.post(url).data["leido_en"], leida)
        self.assertEqual(self.client.get(self.url).data["no_leidas"], 1)
        self.assertEqual(self.client.post(self.url + "leer-todas/").data["actualizadas"], 1)
        self.assertEqual(self.client.get(self.url).data["no_leidas"], 0)
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.get(self.url).data["no_leidas"], 3)

    def test_sse_publica_solo_los_cambios_del_titular(self):
        import json
        import time

        self.client.force_authenticate(self.adoptante, token={"exp": time.time() + 10})
        response = self.client.get(self.url + "eventos/", HTTP_ACCEPT="text/event-stream")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response["Content-Type"], "text/event-stream")
        stream = iter(response.streaming_content)
        self.assertTrue(next(stream).startswith(b"event: sincronizar"))

        otra = SolicitudAdopcion.objects.create(
            adoptante=self.otro, mascota=self.mascota, albergue=self.albergue,
        )
        try:
            SolicitudEvento.objects.create(solicitud=otra, tipo="SOLICITUD_CREADA")
            cambiar_estado_solicitud(self.solicitud.pk, "RECHAZADA", self.staff, "Falta información")
            trama = next(stream).decode()
            self.assertTrue(trama.startswith("event: avisos\n"))
            self.assertEqual(json.loads(trama.split("data: ", 1)[1]), {"solicitudes": [self.solicitud.pk]})
            self.assertNotIn("Falta información", trama)
        finally:
            response.close()

        self.client.force_authenticate(self.staff, token={"exp": time.time() + 10})
        self.assertEqual(self.client.get(self.url + "eventos/").status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(self.url + "eventos/").status_code, 401)

    def test_canal_sse_se_revierte_si_no_se_confirma_la_decision(self):
        from apps.acceso.models import CanalAvisos

        self.evento()
        antes = CanalAvisos.objects.get(adoptante=self.adoptante).revision
        with self.assertRaises(RuntimeError):
            with transaction.atomic():
                cambiar_estado_solicitud(self.solicitud.pk, "RECHAZADA", self.staff, "Sin disponibilidad")
                raise RuntimeError("rollback")
        self.assertEqual(CanalAvisos.objects.get(adoptante=self.adoptante).revision, antes)
