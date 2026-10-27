from datetime import timedelta
from django.test import TestCase
from django.contrib.auth import get_user_model
from django.utils import timezone
from rest_framework.test import APIClient
from apps.albergues.models import Albergue, EventoAdopcion

class EventosTest(TestCase):
    def setUp(self):
        User=get_user_model()
        self.owner=User.objects.create_user(username='event-owner',rol='ALBERGUE')
        self.other=User.objects.create_user(username='event-other',rol='ALBERGUE')
        Albergue.objects.create(usuario=self.owner,nombre='Hogar',direccion='Lima')
        Albergue.objects.create(usuario=self.other,nombre='Otro',direccion='Lima')
        self.client=APIClient();self.client.force_authenticate(self.owner)
        now=timezone.now()+timedelta(days=1)
        self.payload=dict(titulo='Jornada',descripcion='Adopción presencial',inicio=now.isoformat(),fin=(now+timedelta(hours=2)).isoformat(),ubicacion='Lima',contacto_publico='contacto@example.test')

    def test_crear_editar_cancelar_y_consulta_publica(self):
        response=self.client.post('/api/eventos/',self.payload,format='json');self.assertEqual(response.status_code,201)
        url=f'/api/eventos/{response.data["id"]}/'
        self.assertEqual(self.client.patch(url,{'titulo':'Jornada actualizada'},format='json').status_code,200)
        self.assertEqual(self.client.patch(url,{'cancelado':True},format='json').status_code,200)
        self.client.force_authenticate(None)
        response=self.client.get(url);self.assertEqual(response.status_code,200);self.assertTrue(response.data['cancelado'])
        self.assertEqual(EventoAdopcion.objects.count(),1)

    def test_otro_albergue_no_modifica_y_anonimo_no_crea(self):
        response=self.client.post('/api/eventos/',self.payload,format='json')
        self.client.force_authenticate(self.other)
        self.assertEqual(self.client.patch(f'/api/eventos/{response.data["id"]}/',{'titulo':'Ajeno'},format='json').status_code,404)
        self.client.force_authenticate(None)
        self.assertEqual(self.client.post('/api/eventos/',self.payload,format='json').status_code,403)

    def test_rechaza_campos_vacios_y_fechas_invalidas(self):
        self.assertEqual(self.client.post('/api/eventos/',{**self.payload,'ubicacion':''},format='json').status_code,400)
        self.assertEqual(self.client.post('/api/eventos/',{**self.payload,'fin':self.payload['inicio']},format='json').status_code,400)
