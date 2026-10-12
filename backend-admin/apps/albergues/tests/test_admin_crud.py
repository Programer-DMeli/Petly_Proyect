from unittest.mock import patch
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota

class AdminCrudTest(TestCase):
    def setUp(self):
        User = get_user_model()
        self.admin = User.objects.create_user(username='admin', rol='ADMIN')
        self.owner = User.objects.create_user(username='owner', rol='ALBERGUE', spring_id=101)
        self.other = User.objects.create_user(username='other', rol='ALBERGUE', spring_id=102)
        self.client = APIClient(); self.client.force_authenticate(self.admin, token='token-test')

    @patch('apps.acceso.authentication.spring_get')
    def test_create_edit_delete_and_owner_preserved(self, authority):
        authority.return_value = {'id':101,'correo':'owner@example.test','rol':'ALBERGUE'}
        r=self.client.post('/api/albergues/', {'nombre':'Refugio nuevo','direccion':'Lima','capacidad_maxima':30,'propietario_spring_id':101},format='json')
        self.assertEqual(r.status_code,201)
        self.assertEqual(r.data['propietario_spring_id'],101)
        self.assertEqual(r.data['usuario_id'],self.owner.id)
        self.assertFalse(get_user_model().objects.get(pk=self.owner.pk).has_usable_password())
        pk=r.data['id']
        self.assertEqual(self.client.patch(f'/api/albergues/{pk}/',{'nombre':'Editado','activo':False},format='json').status_code,200)
        self.assertEqual(self.client.get('/api/albergues/?search=Editado').data['count'],1)
        self.assertEqual(self.client.delete(f'/api/albergues/{pk}/').status_code,204)
        self.assertTrue(get_user_model().objects.filter(pk=self.owner.pk).exists())

    @patch('apps.acceso.authentication.spring_get')
    def test_duplicate_owner_and_technical_owner_rejected(self, authority):
        authority.return_value = {'id':101,'correo':'owner@example.test','rol':'ALBERGUE'}
        Albergue.objects.create(usuario=self.owner,nombre='Existe',direccion='Lima')
        data={'nombre':'Duplicado','direccion':'Lima','propietario_spring_id':101}
        self.assertEqual(self.client.post('/api/albergues/',data,format='json').status_code,400)
        self.other.is_staff=True;self.other.save()
        authority.return_value = {'id':102,'correo':'other@example.test','rol':'ALBERGUE'}
        data['propietario_spring_id']=102
        self.assertEqual(self.client.post('/api/albergues/',data,format='json').status_code,400)

    @patch('apps.acceso.authentication.spring_get')
    def test_authority_denies_wrong_role_and_accounts_only_admin(self, authority):
        import urllib.error
        authority.side_effect=urllib.error.HTTPError('test',404,'not found',{},None)
        self.assertEqual(self.client.post('/api/albergues/',{'nombre':'No','direccion':'Lima','propietario_spring_id':999},format='json').status_code,400)
        self.client.force_authenticate(self.owner,token='test')
        self.assertEqual(self.client.get('/api/albergues/cuentas-disponibles/').status_code,403)
        self.assertEqual(self.client.post('/api/albergues/',{'nombre':'No','direccion':'Lima','propietario_spring_id':101},format='json').status_code,400)

    def test_delete_with_pets_rejected_and_other_owner_isolated(self):
        albergue=Albergue.objects.create(usuario=self.owner,nombre='Con mascotas',direccion='Lima')
        Mascota.objects.create(albergue=albergue,nombre='Luna',codigo='PROTECTED',especie='PERRO',sexo='POR_EVAL')
        self.assertEqual(self.client.delete(f'/api/albergues/{albergue.pk}/').status_code,409)
        self.assertTrue(Albergue.objects.filter(pk=albergue.pk).exists())
        self.client.force_authenticate(self.other)
        self.assertEqual(self.client.patch(f'/api/albergues/{albergue.pk}/',{'nombre':'No'}).status_code,404)

    @patch('apps.albergues.views.spring_get')
    def test_account_list_flags_linked_accounts(self, authority):
        Albergue.objects.create(usuario=self.owner,nombre='Existe',direccion='Lima')
        authority.return_value=[{'id':101,'correo':'owner@example.test','rol':'ALBERGUE'},{'id':102,'correo':'other@example.test','rol':'ALBERGUE'}]
        r=self.client.get('/api/albergues/cuentas-disponibles/')
        self.assertEqual(r.status_code,200)
        self.assertEqual([v['asignada'] for v in r.data],[True,False])
