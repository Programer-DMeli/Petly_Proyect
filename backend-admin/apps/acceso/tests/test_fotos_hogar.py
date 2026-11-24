import io
import json
import tempfile
from urllib.error import URLError
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
        response = self.client.get(archivo)
        self.assertEqual(response.status_code,200)
        response.close()
        self.client.force_authenticate(self.owner)
        response=self.client.get(archivo);self.assertEqual(response.status_code,200)
        self.assertEqual(response['Cache-Control'],'private, no-store')
        response.close()
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen()}).status_code,403)
        self.permiso.estado='REVOCADA';self.permiso.save()
        response = self.client.get(archivo)
        self.assertEqual(response.status_code,403)
        response.close()
        self.client.force_authenticate(self.otro)
        response = self.client.get(archivo)
        self.assertEqual(response.status_code,403)
        response.close()
        self.client.force_authenticate(None)
        response = self.client.get(archivo)
        self.assertIn(response.status_code,[401,403])
        response.close()

    def test_limite_vigente_de_spring_aplica_a_la_carga(self):
        foto = self.imagen().read() + b"\0" * (1024 * 1024)

        def archivo():
            return SimpleUploadedFile("hogar.png", foto, content_type="image/png")

        with self.settings(PETLY_TEST_MAX_IMAGE_BYTES=None), patch(
            "config.parametros_sistema.urllib.request.urlopen",
            side_effect=[
                io.BytesIO(json.dumps({"maxImagenMb": 1}).encode()),
                io.BytesIO(json.dumps({"maxImagenMb": 2}).encode()),
                URLError("sin conexión"),
            ],
        ):
            rechazo = self.client.post(self.url, {"archivo": archivo()})
            self.assertEqual(rechazo.status_code, 400)
            self.assertIn("1 MB", rechazo.data["archivo"])
            self.assertFalse(FotoHogar.objects.exists())
            self.assertEqual(self.client.post(self.url, {"archivo": archivo()}).status_code, 201)
            self.assertEqual(self.client.post(self.url, {"archivo": self.imagen()}).status_code, 503)
            self.assertEqual(FotoHogar.objects.count(), 1)

    def test_no_subir_despues_de_decision(self):
        self.solicitud.estado='APROBADA';self.solicitud.save()
        self.assertEqual(self.client.post(self.url,{'archivo':self.imagen()}).status_code,400)

    def test_formulario_privado_no_se_expone_tras_revocar(self):
        self.solicitud.respuestas_cuestionario={"direccionHogar":"Dirección privada"};self.solicitud.save()
        self.client.force_authenticate(self.owner)
        url=f"/api/acceso/solicitudes/{self.solicitud.pk}/"
        detalle = self.client.get(url)
        self.assertIn("respuestas_cuestionario", detalle.data)
        self.assertEqual(detalle["Cache-Control"], "private, no-store")
        resumen = self.client.get(url, {"vista": "resumen"})
        self.assertEqual(resumen.data["id"], self.solicitud.pk)
        self.assertNotIn("respuestas_cuestionario", resumen.data)
        listado = self.client.get("/api/acceso/solicitudes/", {"vista": "resumen"}).data
        self.assertNotIn("respuestas_cuestionario", listado["results"][0])
        self.permiso.estado="REVOCADA";self.permiso.save()
        self.assertNotIn("respuestas_cuestionario",self.client.get(url).data)
