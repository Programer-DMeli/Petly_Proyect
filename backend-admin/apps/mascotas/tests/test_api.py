"""Tests de API para Mascotas."""
import json

from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient
from rest_framework import status
from unittest.mock import patch
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota
from apps.mascotas.descripcion_ia import ProveedorDescripcionNoConfigurado
from apps.mascotas.foto_ia import ProveedorFotoNoConfigurado

from django.core.files.uploadedfile import SimpleUploadedFile
from PIL import Image
from io import BytesIO

def foto_prueba(nombre="mascota.png"):
    out = BytesIO(); Image.new("RGB", (10,10)).save(out, format="PNG")
    return SimpleUploadedFile(nombre, out.getvalue(), content_type="image/png")

User = get_user_model()


class MascotaAPITest(TestCase):
    """Tests de endpoints API de Mascotas."""

    def setUp(self):
        self.user = User.objects.create_user(username='owner', password='pass123', rol='ALBERGUE')
        self.other_user = User.objects.create_user(username='other', password='pass123', rol='ALBERGUE')
        self.staff = User.objects.create_user(username='staff', password='pass123', is_staff=True, rol='ADMIN')
        
        self.albergue = Albergue.objects.create(
            usuario=self.user,
            nombre='Mi Albergue',
            direccion='Calle 123',
        )
        self.mascota = Mascota.objects.create(
            albergue=self.albergue,
            nombre='Firulais',
            codigo='PET-001',
            especie=Mascota.Especie.PERRO,
            sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.DISPONIBLE,
            creado_por=self.user,
        )
        self.client = APIClient()

    def _auth(self, user):
        self.client.force_authenticate(user=user)

    def test_listar_mis_mascotas(self):
        """Usuario ve solo mascotas de su albergue (endpoint mis-mascotas con paginación)."""
        Mascota.objects.create(
            albergue=self.albergue, nombre='Otra', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
        )
        # Crear mascota de otro albergue
        albergue2 = Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        Mascota.objects.create(
            albergue=albergue2, nombre='Ajena', codigo='PET-003',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
        )
        
        self._auth(self.user)
        response = self.client.get('/api/mascotas/mis-mascotas/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        # response.data puede ser ReturnList (paginado) o dict con 'results'
        if hasattr(response.data, 'get'):
            data = response.data.get('results', response.data)
        else:
            data = response.data
        self.assertEqual(len(data), 2)
        codigos = [m['codigo'] for m in data]
        self.assertIn('PET-001', codigos)
        self.assertIn('PET-002', codigos)
        self.assertNotIn('PET-003', codigos)

    def test_crear_mascota_asigna_albergue(self):
        """Al crear, se asigna el albergue del usuario."""
        self._auth(self.user)
        data = {
            'nombre': 'Nueva', 'codigo': 'PET-010',
            'especie': 'GATO', 'sexo': 'HEMBRA',
            'edad_anos': 1,
            'foto_principal': foto_prueba(),
        }
        response = self.client.post('/api/mascotas/', data)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        # El serializer de lectura incluye el campo albergue
        self.assertIn('albergue', response.data)
        self.assertEqual(response.data['albergue'], self.albergue.id)

    def test_ficha_veterinaria_se_guarda_se_consulta_y_se_edita(self):
        self._auth(self.user)
        payload = {
            'nombre': 'Nube', 'especie': 'GATO', 'sexo': 'HEMBRA',
            'foto_principal': foto_prueba('nube.png'),
            'estado_salud_general': 'En buen estado; control veterinario reciente.',
            'vacunas_al_dia': 'true', 'esterilizado': 'true', 'desparasitado': 'false',
            'necesidades_especiales': 'Dieta húmeda.',
            'evidencia_vacunacion': foto_prueba('vacunas.png'),
            'evidencia_esterilizacion': foto_prueba('esterilizacion.png'),
        }
        created = self.client.post('/api/mascotas/', payload, format='multipart')
        self.assertEqual(created.status_code, status.HTTP_201_CREATED, created.data)
        pet_id = created.data['id']
        self.assertTrue(created.data['evidencia_vacunacion'])
        self.assertTrue(created.data['evidencia_esterilizacion'])

        opened = self.client.get(f'/api/mascotas/{pet_id}/')
        self.assertEqual(opened.data['estado_salud_general'], payload['estado_salud_general'])
        self.assertTrue(opened.data['vacunas_al_dia'])
        self.assertEqual(opened.data['necesidades_especiales'], 'Dieta húmeda.')

        updated = self.client.patch(f'/api/mascotas/{pet_id}/', {
            'estado_salud_general': 'Control actualizado.',
            'evidencia_esterilizacion': foto_prueba('esterilizacion-nueva.png'),
            'eliminar_evidencia_vacunacion': 'true',
        }, format='multipart')
        self.assertEqual(updated.status_code, status.HTTP_200_OK, updated.data)
        self.assertEqual(updated.data['estado_salud_general'], 'Control actualizado.')
        self.assertIsNone(updated.data['evidencia_vacunacion'])
        self.assertIn('esterilizacion-nueva', updated.data['evidencia_esterilizacion'])

    def test_evidencia_veterinaria_rechaza_imagen_invalida(self):
        self._auth(self.user)
        response = self.client.post('/api/mascotas/', {
            'nombre': 'Nube', 'especie': 'GATO', 'sexo': 'HEMBRA',
            'foto_principal': foto_prueba(),
            'evidencia_vacunacion': SimpleUploadedFile('invalida.png', b'no es una imagen', content_type='image/png'),
        }, format='multipart')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('evidencia_vacunacion', response.data)

    def test_historial_clinico_se_guarda_junto_con_la_ficha_multipart(self):
        self._auth(self.user)
        registros = [
            {'tipo': 'VACUNACION', 'fecha': '2024-05-03'},
            {'tipo': 'ESTERILIZACION', 'fecha': '2024-05-02'},
            {'tipo': 'DESPARASITACION', 'fecha': '2024-05-01'},
        ]
        creada = self.client.post('/api/mascotas/', {
            'nombre': 'Nube', 'especie': 'GATO', 'sexo': 'HEMBRA',
            'foto_principal': foto_prueba('nube.png'),
            'evidencia_vacunacion': foto_prueba('vacunas.png'),
            'vacunas_al_dia': 'true',
            'registros_clinicos': json.dumps(registros),
        }, format='multipart')
        self.assertEqual(creada.status_code, status.HTTP_201_CREATED, creada.data)
        url = f"/api/mascotas/{creada.data['id']}/"
        ficha = self.client.get(url).data
        self.assertEqual(ficha['registros_clinicos'], creada.data['registros_clinicos'])
        self.assertEqual([
            {'tipo': item['tipo'], 'fecha': item['fecha']} for item in ficha['registros_clinicos']
        ], registros)
        self.assertTrue(ficha['vacunas_al_dia'])
        self.assertTrue(ficha['evidencia_vacunacion'])
        anterior = ficha['registros_clinicos'][0]
        editada = self.client.patch(url, {
            'registros_clinicos': json.dumps([{**anterior, 'fecha': '2024-05-04'}]),
        }, format='multipart')
        self.assertEqual(editada.status_code, status.HTTP_200_OK, editada.data)
        self.assertEqual(self.client.get(url).data['registros_clinicos'], [
            {**anterior, 'fecha': '2024-05-04'}, *ficha['registros_clinicos'][1:],
        ])

    def test_historial_clinico_respeta_mascota_y_albergue(self):
        otra = Mascota.objects.create(
            albergue=self.albergue, nombre='Otra', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
        )
        self._auth(self.user)
        url = f'/api/mascotas/{self.mascota.pk}/'
        otra_url = f'/api/mascotas/{otra.pk}/'
        datos = {'tipo': 'VACUNACION', 'fecha': '2024-03-01'}
        primera = self.client.patch(url, {'registros_clinicos': [datos]}, format='json')
        segunda = self.client.patch(otra_url, {'registros_clinicos': [datos]}, format='json')
        self.assertEqual(primera.status_code, status.HTTP_200_OK, primera.data)
        self.assertEqual(segunda.status_code, status.HTTP_200_OK, segunda.data)
        registro_propio = primera.data['registros_clinicos'][0]
        registro_ajeno = segunda.data['registros_clinicos'][0]
        correccion = {**registro_propio, 'fecha': '2024-03-02'}
        rechazo = self.client.patch(url, {
            'nombre': 'Cambio inválido', 'registros_clinicos': [correccion, registro_ajeno],
        }, format='json')
        self.assertEqual(rechazo.status_code, status.HTTP_400_BAD_REQUEST, rechazo.data)
        ficha = self.client.get(url).data
        self.assertEqual(ficha['nombre'], self.mascota.nombre)
        self.assertEqual(ficha['registros_clinicos'], [registro_propio])
        editada = self.client.patch(url, {'registros_clinicos': [correccion]}, format='json')
        self.assertEqual(editada.data['registros_clinicos'], [correccion])
        self.assertEqual(self.client.get(otra_url).data['registros_clinicos'], [registro_ajeno])

        Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        self._auth(self.other_user)
        self.assertEqual(self.client.get(url).status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(self.client.patch(url, {'registros_clinicos': [correccion]}, format='json').status_code,
                         status.HTTP_404_NOT_FOUND)

    def test_ficha_anterior_sin_registros_conserva_sus_indicadores(self):
        self.mascota.vacunas_al_dia = True
        self.mascota.esterilizado = True
        self.mascota.desparasitado = True
        self.mascota.save(update_fields=['vacunas_al_dia', 'esterilizado', 'desparasitado'])
        self._auth(self.user)
        url = f'/api/mascotas/{self.mascota.pk}/'
        ficha = self.client.get(url).data
        self.assertEqual(ficha['registros_clinicos'], [])
        for campo in ('vacunas_al_dia', 'esterilizado', 'desparasitado'):
            self.assertTrue(ficha[campo])
        respuesta = self.client.patch(url, {'estado_salud_general': 'Control normal.'}, format='json')
        self.assertEqual(respuesta.status_code, status.HTTP_200_OK, respuesta.data)
        self.assertEqual(respuesta.data['registros_clinicos'], [])

    def test_historial_clinico_rechaza_tipos_y_fechas_invalidos(self):
        self._auth(self.user)
        url = f'/api/mascotas/{self.mascota.pk}/'
        for registro in (
            {'tipo': 'OTRO', 'fecha': '2024-01-01'},
            {'tipo': 'VACUNACION', 'fecha': '2024-02-30'},
            {'tipo': 'VACUNACION', 'fecha': '9999-01-01'},
            {'tipo': 'VACUNACION', 'fecha': '0999-01-01'},
            {'tipo': 'VACUNACION'},
            'no es un registro',
        ):
            with self.subTest(registro=registro):
                respuesta = self.client.patch(url, {'registros_clinicos': [registro]}, format='json')
                self.assertEqual(respuesta.status_code, status.HTTP_400_BAD_REQUEST, respuesta.data)
                self.assertIn('registros_clinicos', respuesta.data)
        self.assertEqual(self.client.get(url).data['registros_clinicos'], [])

    @patch(
        "apps.mascotas.descripcion_ia.generar_borrador",
        return_value="Pelaje dorado y orejas caídas visibles en la fotografía.",
    )
    def test_generar_descripcion_entrega_borrador_sin_guardarlo(self, generar):
        self._auth(self.user)
        self.mascota.descripcion = "Descripción aprobada por el albergue."
        self.mascota.save(update_fields=["descripcion"])

        response = self.client.post(f"/api/mascotas/{self.mascota.pk}/generar-descripcion/")

        self.assertEqual(response.status_code, status.HTTP_200_OK, response.data)
        self.assertEqual(response.data["borrador"], "Pelaje dorado y orejas caídas visibles en la fotografía.")
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.descripcion, "Descripción aprobada por el albergue.")
        generar.assert_called_once_with(self.mascota)

    def test_otro_albergue_no_puede_generar_descripcion_de_mascota_ajena(self):
        otro_albergue = Albergue.objects.create(
            usuario=self.other_user, nombre="Otro albergue", direccion="Calle 2"
        )
        otra_mascota = Mascota.objects.create(
            albergue=otro_albergue, nombre="Luna", codigo="PET-OTRA",
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
        )
        self._auth(self.user)

        response = self.client.post(f"/api/mascotas/{otra_mascota.pk}/generar-descripcion/")

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    @patch(
        "apps.mascotas.descripcion_ia.generar_borrador",
        side_effect=ProveedorDescripcionNoConfigurado("sin proveedor"),
    )
    def test_generar_descripcion_informa_servicio_no_configurado(self, generar):
        self._auth(self.user)

        response = self.client.post(f"/api/mascotas/{self.mascota.pk}/generar-descripcion/")

        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        generar.assert_called_once()

    @patch("apps.mascotas.foto_ia.mejorar_foto", return_value={
        "mime_type": "image/png", "nombre": "Firulais-mejorada.png",
        "base64": "aW1hZ2Vu", "data_url": "data:image/png;base64,aW1hZ2Vu",
        "ancho": 10, "alto": 10,
    })
    def test_mejorar_foto_devuelve_preview_sin_reemplazar_original(self, mejorar):
        self.mascota.foto_principal = foto_prueba()
        self.mascota.save(update_fields=["foto_principal"])
        original = self.mascota.foto_principal.name
        self._auth(self.user)

        response = self.client.post(f"/api/mascotas/{self.mascota.pk}/mejorar-foto/")

        self.assertEqual(response.status_code, status.HTTP_200_OK, response.data)
        self.assertFalse(response.data["guardada"])
        self.assertEqual(response.data["foto_mejorada"]["data_url"], "data:image/png;base64,aW1hZ2Vu")
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.foto_principal.name, original)
        mejorar.assert_called_once_with(self.mascota)

    def test_confirmar_foto_mejorada_persiste_referencias_original_y_mejorada(self):
        self._auth(self.user)
        self.mascota.foto_principal = foto_prueba("original.png")
        self.mascota.save(update_fields=["foto_principal"])
        original_name = self.mascota.foto_principal.name

        response = self.client.patch(
            f"/api/mascotas/{self.mascota.pk}/",
            {"foto_principal": foto_prueba("Firulais-mejorada.png"), "foto_mejorada_confirmada": "true"},
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK, response.data)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.foto_original_ia.name, original_name)
        self.assertEqual(self.mascota.foto_mejorada_ia.name, self.mascota.foto_principal.name)
        self.assertTrue(self.mascota.foto_original_ia.storage.exists(original_name))
        self.assertIn("foto_original_ia", response.data)
        self.assertIn("foto_mejorada_ia", response.data)

    def test_reemplazar_foto_manualmente_limpia_referencias_ia_anteriores(self):
        self._auth(self.user)
        self.mascota.foto_principal = foto_prueba("original.png")
        self.mascota.foto_original_ia = foto_prueba("ia-original.png")
        self.mascota.foto_mejorada_ia = foto_prueba("ia-mejorada.png")
        self.mascota.save()

        response = self.client.patch(
            f"/api/mascotas/{self.mascota.pk}/",
            {"foto_principal": foto_prueba("reemplazo-manual.png")},
            format="multipart",
        )

        self.assertEqual(response.status_code, status.HTTP_200_OK, response.data)
        self.mascota.refresh_from_db()
        self.assertFalse(self.mascota.foto_original_ia.name)
        self.assertFalse(self.mascota.foto_mejorada_ia.name)

    @patch("apps.mascotas.foto_ia.mejorar_foto")
    def test_otro_albergue_no_puede_mejorar_foto_ajena(self, mejorar):
        Albergue.objects.create(
            usuario=self.other_user, nombre="Otro albergue", direccion="Calle 2"
        )
        self._auth(self.other_user)

        response = self.client.post(f"/api/mascotas/{self.mascota.pk}/mejorar-foto/")

        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)
        mejorar.assert_not_called()

    @patch(
        "apps.mascotas.foto_ia.mejorar_foto",
        side_effect=ProveedorFotoNoConfigurado("sin proveedor"),
    )
    def test_mejorar_foto_informa_servicio_no_configurado(self, mejorar):
        self._auth(self.user)

        response = self.client.post(f"/api/mascotas/{self.mascota.pk}/mejorar-foto/")

        self.assertEqual(response.status_code, status.HTTP_503_SERVICE_UNAVAILABLE)
        mejorar.assert_called_once_with(self.mascota)

    def test_cambiar_estado_transicion_valida(self):
        """Cambio DISPONIBLE -> EN_PROCESO permitido."""
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': Mascota.Estado.EN_PROCESO}
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['estado'], Mascota.Estado.EN_PROCESO)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.EN_PROCESO)

    def test_cambiar_estado_transicion_invalida(self):
        """Cambio DISPONIBLE -> (algo inválido) falla."""
        self.mascota.estado = Mascota.Estado.ADOPTADO
        self.mascota.save()
        
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': 'INVALIDO'}
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        # DRF devuelve errores a nivel de campo para ChoiceField inválido
        self.assertIn('estado', response.data)

    def test_cambiar_estado_adoptado_fecha_adopcion(self):
        """No se puede finalizar una adopción sin registrar entrega de contrato firmado."""
        self._auth(self.user)
        response = self.client.post(
            f'/api/mascotas/{self.mascota.id}/cambiar-estado/',
            {'estado': Mascota.Estado.ADOPTADO}
        )
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.estado, Mascota.Estado.DISPONIBLE)
        self.assertIsNone(self.mascota.fecha_adopcion)

    def test_estadisticas_endpoint(self):
        """Endpoint estadísticas retorna conteos correctos."""
        Mascota.objects.create(
            albergue=self.albergue, nombre='M2', codigo='PET-002',
            especie=Mascota.Especie.GATO, sexo=Mascota.Sexo.HEMBRA,
            estado=Mascota.Estado.EN_PROCESO,
        )
        Mascota.objects.create(
            albergue=self.albergue, nombre='M3', codigo='PET-003',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
            estado=Mascota.Estado.ADOPTADO,
        )
        
        self._auth(self.user)
        response = self.client.get('/api/mascotas/estadisticas/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['total'], 3)
        self.assertEqual(response.data['disponibles'], 1)
        self.assertEqual(response.data['en_proceso'], 1)
        self.assertEqual(response.data['adoptados'], 1)
        self.assertEqual(response.data['por_especie']['perros'], 2)
        self.assertEqual(response.data['por_especie']['gatos'], 1)

    def test_no_puede_ver_mascotas_ajenas(self):
        """Usuario no ve mascotas de otro albergue en detalle."""
        albergue2 = Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        mascota_ajena = Mascota.objects.create(
            albergue=albergue2, nombre='Ajena', codigo='PET-999',
            especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.MACHO,
        )
        
        self._auth(self.user)
        response = self.client.get(f'/api/mascotas/{mascota_ajena.id}/')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_otro_albergue_no_puede_modificar_ni_eliminar_mascota(self):
        Albergue.objects.create(usuario=self.other_user, nombre='A2', direccion='D2')
        self._auth(self.other_user)
        path = f'/api/mascotas/{self.mascota.id}/'
        self.assertEqual(
            self.client.patch(path, {'nombre': 'Tomada'}, format='json').status_code,
            status.HTTP_404_NOT_FOUND,
        )
        self.assertEqual(self.client.delete(path).status_code, status.HTTP_404_NOT_FOUND)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.nombre, 'Firulais')

    def test_foto_y_edad_invalidas_rechazadas(self):
        self._auth(self.user)
        payload = {"nombre": "Luna", "especie": "PERRO", "sexo": "HEMBRA", "edad_anos": 2}
        # La foto válida es obligatoria al crear y las edades respetan el rango.
        self.assertEqual(self.client.post('/api/mascotas/', payload).status_code, 400)
        payload["foto_principal"] = foto_prueba()
        self.assertEqual(self.client.post('/api/mascotas/', payload).status_code, 201)
        payload["foto_principal"] = SimpleUploadedFile("fake.png",b"not an image",content_type="image/png")
        self.assertEqual(self.client.post('/api/mascotas/',payload).status_code,400)
        payload["foto_principal"] = foto_prueba(); payload["edad_meses"] = 12
        self.assertEqual(self.client.post('/api/mascotas/',payload).status_code,400)

    def test_edad_total_y_filtros_invalidos(self):
        self._auth(self.user)
        self.mascota.edad_anos = 2
        self.mascota.edad_meses = 3
        self.mascota.save()
        r = self.client.get('/api/mascotas/?edad_min_meses=27&edad_max_meses=27')
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data['count'], 1)
        self.assertEqual(self.client.get('/api/mascotas/?edad_max_meses=3').data['count'], 0)
        for query in ['edad_min_meses=x','edad_min_meses=-1','edad_min_meses=28&edad_max_meses=27']:
            self.assertEqual(self.client.get('/api/mascotas/?'+query).status_code, 400)

    def test_revertir_adoptado_limpia_fecha(self):
        import datetime
        self._auth(self.user)
        self.mascota.estado = Mascota.Estado.ADOPTADO
        self.mascota.fecha_adopcion = datetime.date.today()
        self.mascota.save()
        r = self.client.post(f'/api/mascotas/{self.mascota.id}/cambiar-estado/', {'estado':'DISPONIBLE'})
        self.assertEqual(r.status_code, 200)
        self.mascota.refresh_from_db()
        self.assertIsNone(self.mascota.fecha_adopcion)
        self.assertEqual(self.mascota.estado, 'DISPONIBLE')

    def test_edicion_multipart_limpia_edad(self):
        self._auth(self.user)
        self.mascota.edad_anos = 2
        self.mascota.save()
        response = self.client.patch(f'/api/mascotas/{self.mascota.id}/', {'edad_anos':'','edad_meses':'','nombre':'Actualizada'}, format='multipart')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data['id'], self.mascota.id)
        self.assertIn('estado', response.data)
        self.mascota.refresh_from_db()
        self.assertIsNone(self.mascota.edad_anos)
        self.assertIsNone(self.mascota.edad_meses)

    @patch('apps.mascotas.gemini.temperamento')
    def test_temperamento_sugerido_no_se_guarda_hasta_confirmar(self, generate):
        self._auth(self.user)
        generate.return_value = {'etiqueta': 'SOCIABLE', 'explicacion': 'Observación registrada.', 'evidencia': 'Juega con otros perros', 'guardado': False}
        url = f'/api/mascotas/{self.mascota.id}/sugerir-temperamento/'
        response = self.client.post(url, {}, format='json')
        self.assertEqual(response.status_code, 200)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.temperamento, 'POR_EVALUAR')
        response = self.client.patch(f'/api/mascotas/{self.mascota.id}/', {'temperamento': 'SOCIABLE'}, format='json')
        self.assertEqual(response.status_code, 200)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.temperamento, 'SOCIABLE')
        Albergue.objects.get_or_create(usuario=self.other_user, defaults={'nombre': 'Otro', 'direccion': 'Lima'})
        self._auth(self.other_user)
        self.assertEqual(self.client.post(url, {}, format='json').status_code, 404)
        self._auth(self.staff)
        self.assertEqual(self.client.post(url, {}, format='json').status_code, 403)

    @patch('apps.mascotas.gemini.temperamento')
    def test_error_temperamento_conserva_etiqueta(self, generate):
        from apps.mascotas.descripcion_ia import ErrorProveedorDescripcion
        generate.side_effect = ErrorProveedorDescripcion('Servicio no disponible')
        self._auth(self.user)
        original = self.mascota.temperamento
        response = self.client.post(f'/api/mascotas/{self.mascota.id}/sugerir-temperamento/', {}, format='json')
        self.assertEqual(response.status_code, 502)
        self.mascota.refresh_from_db()
        self.assertEqual(self.mascota.temperamento, original)
