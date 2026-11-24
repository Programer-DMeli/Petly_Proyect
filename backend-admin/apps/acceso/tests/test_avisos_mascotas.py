from io import BytesIO
from tempfile import TemporaryDirectory

from PIL import Image
from django.db import IntegrityError, transaction
from django.core.cache import cache
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from django.urls import reverse
from rest_framework.test import APITestCase
from apps.acceso.models import CanalAvisos, CriteriosAviso, SolicitudEvento, User
from apps.acceso.avisos_mascotas import avisar_nueva_mascota
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


class AvisosMascotasTest(APITestCase):
    url = '/api/acceso/notificaciones/'

    def setUp(self):
        cache.clear()
        self.user = User.objects.create_user(username='adoptante')
        self.other = User.objects.create_user(username='otro')
        CriteriosAviso.objects.create(adoptante=self.other, activo=False)
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

    def test_alta_desde_albergue_publica_aviso_y_actualiza_canal(self):
        self.assertEqual(self.save_filters().status_code, 200)
        self.client.force_authenticate(self.albergue.usuario)
        image = BytesIO()
        Image.new('RGB', (64, 64)).save(image, 'PNG')
        with TemporaryDirectory() as media_root, override_settings(MEDIA_ROOT=media_root):
            alta = self.client.post('/api/mascotas/', {
                'nombre': 'Luna', 'especie': 'PERRO', 'sexo': 'HEMBRA',
                'tamanio': 'MEDIANO', 'temperamento': 'TRANQUILO',
                'edad_anos': 1, 'edad_meses': 0,
                'foto_principal': SimpleUploadedFile('luna.png', image.getvalue(), 'image/png'),
            }, format='multipart')
        self.assertEqual(alta.status_code, 201, alta.data)
        self.assertEqual(CanalAvisos.objects.get(adoptante=self.user).revision, 1)
        self.assertFalse(CanalAvisos.objects.filter(adoptante=self.other).exists())
        self.client.force_authenticate(self.user)
        response = self.client.get(self.url).data
        self.assertEqual(response['no_leidas'], 1)
        self.assertEqual(response['results'][0]['mascota_id'], alta.data['id'])
        self.assertEqual(response['results'][0]['albergue_nombre'], self.albergue.nombre)

    def test_publica_un_aviso_general_para_un_adoptante_sin_criterios(self):
        self.assertTrue(self.client.get(self.url + 'criterios/').data['activo'])
        self.assertFalse(CriteriosAviso.objects.filter(adoptante=self.user).exists())
        self.client.force_authenticate(self.albergue.usuario)
        image = BytesIO()
        Image.new('RGB', (64, 64)).save(image, 'PNG')
        with TemporaryDirectory() as media_root, override_settings(MEDIA_ROOT=media_root):
            alta = self.client.post('/api/mascotas/', {
                'nombre': 'Polo', 'especie': 'GATO', 'sexo': 'MACHO',
                'foto_principal': SimpleUploadedFile('polo.png', image.getvalue(), 'image/png'),
            }, format='multipart')
        self.assertEqual(alta.status_code, 201, alta.data)
        pet = Mascota.objects.get(pk=alta.data['id'])
        avisar_nueva_mascota(Mascota, pet, created=True)
        self.assertEqual(CanalAvisos.objects.get(adoptante=self.user).revision, 1)
        self.client.force_authenticate(self.user)
        response = self.client.get(self.url).data
        self.assertEqual(response['no_leidas'], 1)
        self.assertEqual(response['results'][0]['tipo'], 'NUEVA_MASCOTA')
        self.assertEqual(response['results'][0]['mascota_id'], pet.pk)
        self.assertEqual(response['results'][0]['mascota_nombre'], 'Polo')
        self.client.force_authenticate(self.other)
        self.assertEqual(self.client.get(self.url).data['results'], [])

    def test_no_notifica_mascota_de_albergue_inactivo(self):
        self.save_filters()
        self.albergue.activo = False
        self.albergue.save(update_fields=['activo'])
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 0)

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
        self.save_filters(activo=False)
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
        for changes in [dict(edad_min_meses=37), dict(edad_max_meses=601), dict(edad_min_meses=-1), dict(especie='DRAGON')]:
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
        CriteriosAviso.objects.filter(adoptante=self.other).update(activo=True, especie='PERRO')
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 2)
        self.other.is_active = False
        self.other.save()
        self.pet()
        self.assertEqual(SolicitudEvento.objects.count(), 3)

    def test_general_con_preferencia_guardada_sin_filtros_es_solo_de_bandeja(self):
        self.user.usuario_spring_id = 50
        self.user.save(update_fields=['usuario_spring_id'])
        self.assertEqual(self.save_filters(
            especie='', tamanio='', temperamento='', edad_min_meses=None, edad_max_meses=None,
        ).status_code, 200)
        self.pet(especie='GATO')
        self.assertEqual(SolicitudEvento.objects.get().tipo, SolicitudEvento.Tipo.NUEVA_MASCOTA)
        with override_settings(INTERNAL_API_SECRET='solo-pruebas'):
            response = self.client.get(reverse('feed_interno_coincidencias'), HTTP_X_INTERNAL_SECRET='solo-pruebas')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, [])
