import json
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import patch
from django.test import SimpleTestCase, override_settings
from django.core.files.uploadedfile import SimpleUploadedFile
from PIL import Image
from apps.mascotas.gemini import temperamento
from apps.mascotas.descripcion_ia import generar_borrador, ErrorProveedorDescripcion, ProveedorDescripcionNoConfigurado


@override_settings(GEMINI_API_KEY='fake-unit-test', GEMINI_MODEL='gemini-2.5-flash', GEMINI_TIMEOUT_SECONDS=3)
class GeminiTest(SimpleTestCase):
    def pet(self, description='Juega con otros perros durante los paseos.'):
        image = BytesIO(); Image.new('RGB', (8, 8)).save(image, format='PNG')
        return SimpleNamespace(foto_principal=SimpleUploadedFile('pet.png', image.getvalue()),
                               descripcion=description, temperamento='POR_EVALUAR', energia='MEDIA', especie='PERRO')

    def response(self, value, finish='STOP'):
        return BytesIO(json.dumps({'candidates': [{'finishReason': finish, 'content': {'parts': [{'text': json.dumps(value)}]}}]}).encode())

    @patch('apps.mascotas.gemini.urlopen')
    def test_photo_sent_to_gemini_and_description_is_draft(self, send):
        send.return_value = self.response({'description': 'Pelaje corto de color marrón.'})
        pet = self.pet()
        self.assertEqual(generar_borrador(pet), 'Pelaje corto de color marrón.')
        payload = json.loads(send.call_args.args[0].data)
        self.assertEqual(payload['contents'][0]['parts'][1]['inline_data']['mime_type'], 'image/png')
        self.assertEqual(send.call_args.kwargs['timeout'], 3)
        self.assertEqual(pet.descripcion, 'Juega con otros perros durante los paseos.')

    @patch('apps.mascotas.gemini.urlopen')
    def test_timeout_and_malformed_keep_stored_description(self, send):
        pet = self.pet(); original = pet.descripcion
        for response in [self.response({'description': ''}), self.response({'description': 'Es saludable.'}), self.response({'description': 'Pelaje blanco.'}, 'MAX_TOKENS'), BytesIO(b'not-json')]:
            send.return_value = response
            pet = self.pet()
            with self.assertRaises(ErrorProveedorDescripcion): generar_borrador(pet)
            self.assertEqual(pet.descripcion, original)
        send.side_effect = TimeoutError()
        with self.assertRaises(ErrorProveedorDescripcion): generar_borrador(self.pet())

    @override_settings(GEMINI_API_KEY='', PET_DESCRIPTION_API_URL='', PET_DESCRIPTION_API_TOKEN='')
    @patch('apps.mascotas.gemini.urlopen')
    def test_not_configured_does_not_call(self, send):
        with self.assertRaises(ProveedorDescripcionNoConfigurado): generar_borrador(self.pet())
        send.assert_not_called()

    @patch('apps.mascotas.gemini.urlopen')
    def test_labels_require_profile_evidence_and_confirmation(self, send):
        send.return_value = self.response({'etiqueta': 'SOCIABLE', 'explicacion': 'Observado durante paseos.', 'evidencia': 'Juega con otros perros'})
        pet = self.pet(); result = temperamento(pet)
        self.assertEqual(result['etiqueta'], 'SOCIABLE'); self.assertFalse(result['guardado'])
        self.assertEqual(pet.temperamento, 'POR_EVALUAR')
        send.return_value = self.response({'etiqueta': 'SOCIABLE', 'explicacion': 'Solo por la foto.', 'evidencia': ''})
        self.assertEqual(temperamento(self.pet('Pelaje marrón.'))['etiqueta'], 'POR_EVALUAR')

    @patch('apps.mascotas.gemini.urlopen')
    def test_reject_unknown_label(self, send):
        send.return_value = self.response({'etiqueta': 'PELIGROSO', 'explicacion': 'No permitida.', 'evidencia': ''})
        with self.assertRaises(ErrorProveedorDescripcion): temperamento(self.pet())

    @patch('apps.mascotas.gemini.urlopen')
    def test_rasgo_visual_no_es_evidencia_y_no_se_filtran_campos_extra(self, send):
        send.return_value = self.response({'etiqueta': 'SOCIABLE', 'explicacion': 'Por su color.', 'evidencia': 'Pelaje marrón con manchas blancas.', 'score': 99})
        result = temperamento(self.pet('Pelaje marrón con manchas blancas.'))
        self.assertEqual(result['etiqueta'], 'POR_EVALUAR')
        self.assertEqual(result['evidencia'], '')
        self.assertNotIn('score', result)

    @patch('apps.mascotas.gemini.urlopen')
    def test_requiere_foto_real_y_no_acepta_respuestas_no_objeto(self, send):
        from apps.mascotas.descripcion_ia import DescripcionIAError
        pet = self.pet(); pet.foto_principal = None
        with self.assertRaises(DescripcionIAError): temperamento(pet)
        send.assert_not_called()
        for value in [[], 'texto', None, {'candidates': None}]:
            send.return_value = BytesIO(json.dumps(value).encode())
            with self.assertRaises(ErrorProveedorDescripcion): generar_borrador(self.pet())
