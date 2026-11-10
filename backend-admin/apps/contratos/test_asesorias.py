from datetime import timedelta

from django.core.cache import cache
from django.utils import timezone
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion, User
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from .models import SeguimientoPostAdopcion, SolicitudAsesoriaVeterinaria


class AsesoriaVeterinariaTest(APITestCase):
    url = "/api/contratos/asesorias-veterinarias/"
    seguimientos_url = "/api/contratos/seguimientos/"

    def setUp(self):
        cache.clear()
        self.adoptante = User.objects.create_user(username="adoptante")
        self.otro = User.objects.create_user(username="otro")
        self.albergue_user = User.objects.create_user(username="albergue", rol="ALBERGUE")
        self.otro_albergue_user = User.objects.create_user(username="otro-albergue", rol="ALBERGUE")
        self.albergue = Albergue.objects.create(usuario=self.albergue_user, nombre="Huellitas", direccion="Lima")
        Albergue.objects.create(usuario=self.otro_albergue_user, nombre="Otro hogar", direccion="Lima")
        mascota = Mascota.objects.create(albergue=self.albergue, nombre="Luna", codigo="ASES-1", especie="PERRO", sexo="HEMBRA")
        self.adopcion = SolicitudAdopcion.objects.create(adoptante=self.adoptante, mascota=mascota, albergue=self.albergue, estado="ENTREGADA")
        self.seguimiento = SeguimientoPostAdopcion.objects.create(
            solicitud=self.adopcion, programado_para=timezone.now() + timedelta(days=1), modalidad="LLAMADA",
        )
        self.client.force_authenticate(self.adoptante)

    def tearDown(self):
        cache.clear()
        super().tearDown()

    def solicitar(self, problema="No se adapta al quedarse sola.", **extra):
        return self.client.post(self.url, {"seguimiento": self.seguimiento.pk, "problema": problema, **extra}, format="json")

    def test_solicitud_se_vincula_al_seguimiento_y_el_albergue_la_consulta(self):
        response = self.solicitar("  No se adapta al quedarse sola.  ")
        self.assertEqual(response.status_code, 201, response.data)
        saved = SolicitudAsesoriaVeterinaria.objects.get()
        self.assertEqual(saved.seguimiento_id, self.seguimiento.pk)
        self.assertEqual(saved.problema, "No se adapta al quedarse sola.")
        self.assertEqual(saved.estado, "PENDIENTE")
        self.assertIsNotNone(saved.creado_en)
        for user in (self.adoptante, self.albergue_user):
            self.client.force_authenticate(user)
            self.assertEqual(self.client.get(self.url).data["results"][0]["id"], saved.pk)
            seguimiento = self.client.get(f"{self.seguimientos_url}{self.seguimiento.pk}/").data
            self.assertEqual(seguimiento["asesorias_veterinarias"][0]["problema"], saved.problema)
            self.assertEqual(seguimiento["albergue_nombre"], "Huellitas")

    def test_otros_adoptantes_y_albergues_no_ven_datos_ajenos(self):
        saved = self.solicitar().data
        for user in (self.otro, self.otro_albergue_user):
            self.client.force_authenticate(user)
            self.assertEqual(self.client.get(self.url).data["results"], [])
            self.assertEqual(self.client.get(f"{self.url}{saved['id']}/").status_code, 404)
            self.assertEqual(self.client.get(self.seguimientos_url).data["results"], [])
        self.client.force_authenticate(self.otro)
        self.assertEqual(self.solicitar().status_code, 400)
        self.assertEqual(SolicitudAsesoriaVeterinaria.objects.count(), 1)

    def test_requiere_problema_no_vacio_y_hasta_2000_caracteres(self):
        for problema in ("", "  \n ", "x" * 2001):
            self.assertEqual(self.solicitar(problema).status_code, 400)
        self.assertEqual(self.client.post(self.url, {"seguimiento": self.seguimiento.pk}).status_code, 400)
        self.assertEqual(SolicitudAsesoriaVeterinaria.objects.count(), 0)
        self.assertEqual(self.solicitar("x" * 2000).status_code, 201)

    def test_rechaza_seguimiento_cancelado_inexistente_o_adopcion_no_entregada(self):
        self.assertEqual(self.solicitar(seguimiento=9999).status_code, 400)
        self.seguimiento.estado = "CANCELADO"
        self.seguimiento.save()
        self.assertEqual(self.solicitar().status_code, 400)
        self.seguimiento.estado = "PROGRAMADO"
        self.seguimiento.save()
        self.adopcion.estado = "APROBADA"
        self.adopcion.save()
        self.assertEqual(self.solicitar().status_code, 400)
        self.assertFalse(SolicitudAsesoriaVeterinaria.objects.exists())

    def test_admite_problemas_despues_de_seguimiento_realizado(self):
        self.seguimiento.estado = "REALIZADO"
        self.seguimiento.save()
        self.assertEqual(self.solicitar().status_code, 201)

    def test_adoptante_no_puede_crear_modificar_ni_cancelar_seguimientos(self):
        self.assertEqual(self.client.post(self.seguimientos_url, {}).status_code, 403)
        detail = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        self.assertEqual(self.client.patch(detail, {"notas": "Cambio"}).status_code, 403)
        self.assertEqual(self.client.delete(detail).status_code, 403)
        self.seguimiento.refresh_from_db()
        self.assertEqual(self.seguimiento.estado, "PROGRAMADO")

    def test_albergue_no_suplanta_adoptante_y_no_puede_mover_asesoria_a_otra_adopcion(self):
        self.solicitar()
        self.client.force_authenticate(self.albergue_user)
        self.assertEqual(self.solicitar().status_code, 403)
        otra = SolicitudAdopcion.objects.create(adoptante=self.otro, mascota=self.adopcion.mascota, albergue=self.albergue, estado="ENTREGADA")
        detail = f"{self.seguimientos_url}{self.seguimiento.pk}/"
        self.assertEqual(self.client.patch(detail, {"solicitud_id": otra.pk}).status_code, 400)
        self.assertEqual(self.client.patch(detail, {"notas": "Coordinar orientación"}).status_code, 200)
        self.seguimiento.refresh_from_db()
        self.assertEqual(self.seguimiento.solicitud_id, self.adopcion.pk)

    def test_requiere_autenticacion_y_no_permite_borrar_el_pedido(self):
        saved = self.solicitar().data
        detail = f"{self.url}{saved['id']}/"
        self.assertEqual(self.client.delete(detail).status_code, 405)
        self.assertEqual(self.client.patch(detail, {"problema": "Otro"}).status_code, 405)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(self.seguimientos_url).status_code, 401)
        self.assertEqual(self.client.get(self.url).status_code, 401)
        self.assertEqual(self.solicitar().status_code, 401)

    def test_adoptante_no_elige_destinatario_ni_estado_inicial(self):
        response = self.solicitar(estado="ATENDIDA", albergue=9999)
        self.assertEqual(response.status_code, 201, response.data)
        saved = SolicitudAsesoriaVeterinaria.objects.get()
        self.assertEqual(saved.estado, "PENDIENTE")
        self.assertEqual(saved.seguimiento.solicitud.albergue_id, self.albergue.pk)

    def test_solo_albergue_responsable_gestiona_estado_y_adoptante_lo_consulta(self):
        saved = self.solicitar().data
        path = f"{self.url}{saved['id']}/cambiar-estado/"
        self.assertEqual(self.client.post(path, {"estado": "EN_ATENCION"}).status_code, 403)
        self.client.force_authenticate(self.otro_albergue_user)
        self.assertEqual(self.client.post(path, {"estado": "EN_ATENCION"}).status_code, 404)
        self.client.force_authenticate(self.albergue_user)
        self.assertEqual(self.client.post(path, {"estado": "INVENTADO"}).status_code, 400)
        self.assertEqual(self.client.post(path, {"estado": "ATENDIDA"}).status_code, 409)
        for estado in ("EN_ATENCION", "ATENDIDA"):
            self.client.force_authenticate(self.albergue_user)
            response = self.client.post(path, {"estado": estado})
            self.assertEqual(response.status_code, 200, response.data)
            self.client.force_authenticate(self.adoptante)
            response = self.client.get(f"{self.seguimientos_url}{self.seguimiento.pk}/")
            self.assertEqual(response.data["asesorias_veterinarias"][0]["estado"], estado)
        self.client.force_authenticate(self.albergue_user)
        self.assertEqual(self.client.post(path, {"estado": "PENDIENTE"}).status_code, 409)
