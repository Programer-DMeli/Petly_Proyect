import io
import tempfile
from unittest.mock import patch
from PIL import Image
from django.test import TestCase
from django.contrib.auth import get_user_model
from django.core.files.storage import FileSystemStorage
from django.core.files.uploadedfile import SimpleUploadedFile
from rest_framework.test import APIClient
from apps.acceso.models import SolicitudAdopcion, FotoHogar, AdoptanteProfile, Autorizacion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota

class FotosHogarTest(TestCase):
    def setUp(self):
        User=get_user_model()
        self.titular=User.objects.create_user(username='fotos-titular',rol='ADOPTANTE')
        self.owner=User.objects.create_user(username='fotos-owner',rol='ALBERGUE')
        self.otro=User.objects.create_user(username='fotos-otro',rol='ALBERGUE')
        self.albergue=Albergue.objects.create(usuario=self.owner,nombre='Hogar',direccion='Lima')
        self.mascota=Mascota.objects.create(albergue=self.albergue,nombre='Foto',codigo='FOTO-HOGAR',especie='PERRO',sexo='HEMBRA')
        self.solicitud=SolicitudAdopcion.objects.create(adoptante=self.titular,mascota=self.mascota,albergue=self.albergue)
        AdoptanteProfile.objects.create(user=self.titular,cuestionario_completado=True)
        self.permiso=Autorizacion.objects.create(adoptante=self.titular,albergue=self.albergue)
        self.client=APIClient();self.client.force_authenticate(self.titular)
        self.url=f'/api/acceso/solicitudes/{self.solicitud.pk}/fotos-hogar/'
        folder=tempfile.TemporaryDirectory();self.addCleanup(folder.cleanup)
        storage=patch.object(FotoHogar._meta.get_field('archivo'),'storage',FileSystemStorage(folder.name));storage.start();self.addCleanup(storage.stop)

    def imagen(self,color='red'):
        stream=io.BytesIO();Image.new('RGB',(20,20),color).save(stream,'PNG')
        return SimpleUploadedFile('hogar.png',stream.getvalue(),content_type='image/png')

    def test_limite_formato_y_reintento_idempotente(self):
        for color in ['red','green','blue']:
            self.assertEqual(self.client.post(self.url,{'archivo':self.imagen(color)}).status_code,201)
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen()}).status_code,200)
        self.assertEqual(FotoHogar.objects.count(),3)
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen('white')}).status_code,400)
        self.assertEqual(self.client.post(self.url,{'archivo':SimpleUploadedFile('fake.png',b'fake')}).status_code,400)
        self.assertEqual(self.client.post(self.url,{'archivo':SimpleUploadedFile('big.png',b'x'*(5*1024*1024+1))}).status_code,400)

    def test_privacidad_y_revocacion(self):
        self.client.post(self.url,{'archivo':self.imagen()})
        archivo=self.client.get(self.url).data[0]['url']
        self.assertEqual(self.client.get(archivo).status_code,200)
        self.client.force_authenticate(self.owner)
        response=self.client.get(archivo);self.assertEqual(response.status_code,200)
        self.assertEqual(response['Cache-Control'],'private, no-store')
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen()}).status_code,403)
        self.permiso.estado='REVOCADA';self.permiso.save()
        self.assertEqual(self.client.get(archivo).status_code,403)
        self.client.force_authenticate(self.otro)
        self.assertEqual(self.client.get(archivo).status_code,403)
        self.client.force_authenticate(None)
        self.assertIn(self.client.get(archivo).status_code,[401,403])

    def test_no_subir_despues_de_decision(self):
        self.solicitud.estado='APROBADA';self.solicitud.save()
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen()}).status_code,400)

    def test_formulario_privado_no_se_expone_tras_revocar(self):
        self.solicitud.respuestas_cuestionario={"direccionHogar":"Dirección privada"};self.solicitud.save()
        self.client.force_authenticate(self.owner)
        url=f"/api/acceso/solicitudes/{self.solicitud.pk}/"
        self.assertIn("respuestas_cuestionario",self.client.get(url).data)
        self.permiso.estado="REVOCADA";self.permiso.save()
        self.assertNotIn("respuestas_cuestionario",self.client.get(url).data)
