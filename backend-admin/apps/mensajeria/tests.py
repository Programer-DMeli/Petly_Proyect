from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, User
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class ChatPorSolicitudTest(APITestCase):
    url = "/api/mensajeria/conversaciones/"

    def setUp(self):
        self.adoptante = User.objects.create_user(username="titular-chat")
        self.albergue = User.objects.create_user(username="albergue-chat", rol=User.Rol.ALBERGUE)
        self.otro = User.objects.create_user(username="visitante-chat")
        self.refugio = Albergue.objects.create(usuario=self.albergue, nombre="Refugio")
        self.luna = self.solicitud("Luna")
        self.nube = self.solicitud("Nube")

    def solicitud(self, nombre):
        mascota = Mascota.objects.create(
            albergue=self.refugio, nombre=nombre, codigo=nombre, especie="PERRO", sexo="HEMBRA",
        )
        return SolicitudAdopcion.objects.create(adoptante=self.adoptante, mascota=mascota, albergue=self.refugio)

    def abrir(self, solicitud, mascota_id=None):
        return self.client.post(self.url, {
            "solicitud_id": solicitud.pk, "mascota_id": mascota_id or solicitud.mascota_id,
        })

    def test_solicitudes_del_mismo_albergue_tienen_conversaciones_distintas(self):
        self.client.force_authenticate(self.adoptante)
        general = self.client.post(self.url, {"albergue_id": self.refugio.pk})
        luna, nube = self.abrir(self.luna), self.abrir(self.nube)
        self.assertEqual((general.status_code, luna.status_code, nube.status_code), (201, 201, 201))
        self.assertEqual(len({general.data["id"], luna.data["id"], nube.data["id"]}), 3)
        self.assertEqual(self.abrir(self.luna).data["id"], luna.data["id"])
        self.assertEqual(self.abrir(self.luna, self.nube.mascota_id).status_code, 400)
        repetida = SolicitudAdopcion.objects.create(
            adoptante=self.adoptante, mascota=self.luna.mascota, albergue=self.refugio,
            estado=SolicitudAdopcion.Estado.CANCELADA,
        )
        self.assertNotEqual(self.abrir(repetida).data["id"], luna.data["id"])

        self.client.force_authenticate(self.albergue)
        chat = self.client.get(f"{self.url}{nube.data['id']}/").data["conversacion"]
        self.assertEqual((chat["mascota"], chat["mascota_id"], chat["solicitud_id"]),
                         ("Nube", self.nube.mascota_id, self.nube.pk))
        self.client.force_authenticate(self.otro)
        self.assertEqual(self.abrir(self.nube).status_code, 403)
        self.assertEqual(self.client.get(f"{self.url}{nube.data['id']}/").status_code, 403)
        self.assertEqual(self.client.post(f"{self.url}{nube.data['id']}/mensajes/", {"texto": "Hola"}).status_code, 403)

    def test_bloqueo_no_se_puede_eludir_cambiando_de_solicitud(self):
        self.client.force_authenticate(self.albergue)
        luna = self.abrir(self.luna).data["id"]
        self.client.post(f"{self.url}{luna}/bloquear/")
        self.client.force_authenticate(self.adoptante)
        nube = self.abrir(self.nube).data
        self.assertFalse(nube["puede_enviar"])
        self.assertEqual(self.client.post(f"{self.url}{nube['id']}/mensajes/", {"texto": "Hola"}).status_code, 423)

    def test_desbloqueo_solo_retira_el_bloqueo_propio_en_todas_las_conversaciones_del_par(self):
        self.client.force_authenticate(self.albergue)
        luna = self.abrir(self.luna).data["id"]
        nube = self.abrir(self.nube).data["id"]
        self.assertEqual(self.client.post(f"{self.url}{luna}/bloquear/").status_code, 200)
        self.client.force_authenticate(self.otro)
        self.assertEqual(self.client.post(f"{self.url}{luna}/desbloquear/").status_code, 403)

        self.client.force_authenticate(self.adoptante)
        self.assertFalse(self.client.get(f"{self.url}{nube}/").data["conversacion"]["bloqueada_por_mi"])
        self.assertEqual(self.client.post(f"{self.url}{nube}/desbloquear/").status_code, 204)
        self.assertEqual(self.client.post(f"{self.url}{luna}/mensajes/", {"texto": "Bloqueado"}).status_code, 423)
        self.assertEqual(self.client.post(f"{self.url}{luna}/bloquear/").status_code, 200)

        self.client.force_authenticate(self.albergue)
        self.assertTrue(self.client.get(f"{self.url}{nube}/").data["conversacion"]["bloqueada_por_mi"])
        for _ in range(2):
            self.assertEqual(self.client.post(f"{self.url}{nube}/desbloquear/").status_code, 204)
        chat = self.client.get(f"{self.url}{luna}/").data["conversacion"]
        self.assertTrue(chat["bloqueada"])
        self.assertFalse(chat["bloqueada_por_mi"])

        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.post(f"{self.url}{luna}/mensajes/", {"texto": "Hola de nuevo"}).status_code, 201)
        self.assertEqual(self.client.post(f"{self.url}{luna}/desbloquear/").status_code, 204)
        self.client.force_authenticate(self.albergue)
        self.assertEqual(self.client.post(f"{self.url}{luna}/mensajes/", {"texto": "Respuesta"}).status_code, 201)
        self.assertEqual(len(self.client.get(f"{self.url}{luna}/").data["mensajes"]), 2)

    def test_contador_suma_entrantes_y_desciende_al_leer_por_rol(self):
        contador = self.url + "no-leidos/"
        self.client.force_authenticate(self.adoptante)
        luna = self.abrir(self.luna).data["id"]
        nube = self.abrir(self.nube).data["id"]
        for chat in (luna, nube):
            respuesta = self.client.post(f"{self.url}{chat}/mensajes/", {"texto": "Consulta"})
            self.assertEqual(respuesta.status_code, 201)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 0})

        self.client.force_authenticate(self.albergue)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 2})
        self.assertEqual(self.client.post(f"{self.url}{luna}/leer/").status_code, 204)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 1})
        self.assertEqual(self.client.post(f"{self.url}{luna}/mensajes/", {"texto": "Respuesta"}).status_code, 201)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 1})

        self.client.force_authenticate(self.otro)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 0})
        self.client.force_authenticate(self.adoptante)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 1})
        self.assertEqual(self.client.post(f"{self.url}{luna}/leer/").status_code, 204)
        self.assertEqual(self.client.get(contador).data, {"no_leidos": 0})
