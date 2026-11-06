from django.db import IntegrityError, transaction
from django.core.cache import cache
from rest_framework.test import APITestCase
from apps.acceso.models import CriteriosAviso, SolicitudEvento, User
from apps.acceso.avisos_mascotas import avisar_nueva_mascota
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class AvisosMascotasTest(APITestCase):
    url = '/api/acceso/notificaciones/'

    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(username='adoptante')
        self.other = User.objects.create_user(username='otro')
        staff = User.objects.create_user(username='albergue', rol='ALBERGUE')
        self.albergue = Albergue.objects.create(usuario=staff, nombre='Huellitas', direccion='Lima')
        self.client.force_authenticate(self.user)
        self.filters = dict(activo=True, especie='PERRO', tamanio='MEDIANO', temperamento='TRANQUILO', edad_min_meses=12, edad_max_meses=36)

    def tearDown(self):
        # DRF throttling uses a cache shared between test cases.
        cache.clear()
        super().tearDown()

    def save_filters(self, **changes):
        return self.client.put(self.url + 'criterios/', self.filters | changes, format='json')

    def pet(self, **changes):
        return Mascota.objects.create(**(dict(albergue=self.albergue, codigo=f'M-{Mascota.objects.count()}', nombre='Luna', especie='PERRO', sexo='HEMBRA', tamanio='MEDIANO', temperamento='TRANQUILO', edad_anos=1, edad_meses=0) | changes))

    def test_coincidencia_solo_titular_y_lectura_idempotente(self):
        self.assertEqual(self.save_filters().status_code, 200)
        pet = self.pet()
        response = self.client.get(self.url).data
        self.assertEqual(response['no_leidas'], 1)
        item = response['results'][0]
        self.assertEqual(item['tipo'], 'NUEVA_COINCIDENCIA')
        self.assertEqual(item['mascota_id'], pet.pk)
        self.assertEqual(item['mascota_nombre'], 'Luna')
        self.assertIsNone(item['solicitud_id'])
        read_url = f"{self.url}{item['id']}/leer/"
        read = self.client.post(read_url).data
        self.assertEqual(self.client.post(read_url).data['leido_en'], read['leido_en'])
        self.assertEqual(self.client.get(self.url).data['no_leidas'], 0)
        self.client.force_authenticate(self.other)
        self.assertEqual(self.client.get(self.url).data['results'], [])
        self.assertEqual(self.client.post(read_url).status_code, 404)
        self.assertFalse(self.client.get(self.url + 'criterios/').data['activo'])

    def test_todos_los_filtros_son_obligatorios_y_edad_desconocida_no_coincide(self):
        self.save_filters()
        for changes in [dict(especie='GATO'), dict(tamanio='GRANDE'), dict(temperamento='ACTIVO'), dict(edad_anos=0, edad_meses=11), dict(edad_anos=3, edad_meses=1), dict(edad_anos=None, edad_meses=None), dict(estado='ADOPTADO')]:
            self.pet(**changes)
        self.assertEqual(self.client.get(self.url).data['results'], [])
        self.pet(edad_anos=3)
        self.assertEqual(self.client.get(self.url).data['no_leidas'], 1)

    def test_no_duplica_al_reintentar_signal_editar_o_cambiar_criterios(self):
        self.save_filters()
        pet = self.pet()
        avisar_nueva_mascota(Mascota, pet, created=True)
        pet.nombre = 'Luna actualizada'
        pet.save()
        self.save_filters()
        self.client.get(self.url)
        self.assertEqual(SolicitudEvento.objects.count(), 1)
        with self.assertRaises(IntegrityError), transaction.atomic():
            SolicitudEvento.objects.create(tipo='NUEVA_COINCIDENCIA', destinatario=self.user, mascota_coincidente=pet)

    def test_no_retroactivo_desactivar_y_criterios_parciales(self):
        self.pet()
        self.save_filters()
        self.assertEqual(SolicitudEvento.objects.count(), 0)
        self.save_filters(activo=False)
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 0)
        self.save_filters(tamanio='', temperamento='', edad_min_meses=None, edad_max_meses=None)
        self.pet(tamanio='', temperamento='POR_EVALUAR', edad_anos=None, edad_meses=None)
        self.assertEqual(SolicitudEvento.objects.count(), 1)

    def test_validacion_permisos_y_persistencia(self):
        for changes in [dict(edad_min_meses=37), dict(edad_max_meses=601), dict(edad_min_meses=-1), dict(especie='DRAGON'), dict(especie='', tamanio='', temperamento='', edad_min_meses=None, edad_max_meses=None)]:
            self.assertEqual(self.save_filters(**changes).status_code, 400)
        self.assertEqual(self.save_filters().status_code, 200)
        self.assertEqual(self.client.get(self.url + 'criterios/').data, self.filters)
        self.user.rol = 'ALBERGUE'
        self.user.save()
        self.assertEqual(self.save_filters().status_code, 403)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.get(self.url + 'criterios/').status_code, 401)

    def test_rollback_revierte_mascota_y_aviso(self):
        self.save_filters()
        with self.assertRaises(RuntimeError), transaction.atomic():
            self.pet()
            raise RuntimeError('rollback')
        self.assertEqual(SolicitudEvento.objects.count(), 0)
        self.assertEqual(Mascota.objects.count(), 0)

    def test_notifica_a_varios_adoptantes_y_excluye_inactivos(self):
        self.save_filters()
        CriteriosAviso.objects.create(adoptante=self.other, activo=True, especie='PERRO')
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 2)
        self.other.is_active = False
        self.other.save()
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 3)
