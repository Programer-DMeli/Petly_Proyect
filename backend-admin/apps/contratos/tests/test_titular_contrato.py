from unittest.mock import patch

from django.test import override_settings
from django.urls import resolve, reverse
from rest_framework.test import APITestCase, APIRequestFactory, force_authenticate

from apps.acceso.models import Postulante, SolicitudAdopcion, SolicitudEvento, User, VerificacionKyc
from apps.albergues.models import Albergue
from apps.contratos.models import ContratoAdopcion
from apps.contratos.providers import ErrorProveedorFirma, ProveedorFirmaREST
from apps.mascotas.models import Mascota


@override_settings(ADOPTION_CONTRACT_TERMS_APPROVED=False)
class TitularContratoTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        cls.albergue_user = User.objects.create_user(
            username="albergue", email="albergue@example.test", rol="ALBERGUE"
        )
        cls.adoptante = User.objects.create_user(
            username="adoptante", email="titular@example.test", usuario_spring_id=991,
        )
        albergue = Albergue.objects.create(usuario=cls.albergue_user, nombre="Refugio", direccion="Lima")
        mascota = Mascota.objects.create(
            albergue=albergue, nombre="Luna", especie=Mascota.Especie.PERRO, sexo=Mascota.Sexo.HEMBRA
        )
        cls.solicitud = SolicitudAdopcion.objects.create(
            adoptante=cls.adoptante, mascota=mascota, albergue=albergue,
            estado=SolicitudAdopcion.Estado.APROBADA,
            respuestas_cuestionario={"dni": "99999999", "dniVerificado": True, "nombreCompleto": "Otro nombre"},
        )

    def generar(self):
        ruta = reverse("generar_contrato", args=[self.solicitud.pk])
        vista = resolve(ruta)
        request = APIRequestFactory().post(ruta)
        force_authenticate(request, user=self.albergue_user)
        return vista.func(request, **vista.kwargs)

    def verificacion(self, estado, **datos):
        campos = {"dni": "01234567", "nombres": " Ana ", "apellidos": " Pérez "} | datos
        return VerificacionKyc.objects.create(adoptante=self.adoptante, estado=estado, **campos)

    def assert_falta(self, mensaje):
        respuesta = self.generar()
        self.assertEqual(respuesta.status_code, 400)
        self.assertIn(mensaje, respuesta.data["detail"][0])
        self.assertFalse(ContratoAdopcion.objects.exists())
        self.assertFalse(SolicitudEvento.objects.filter(tipo=SolicitudEvento.Tipo.CONTRATO_GENERADO).exists())

    def test_usa_kyc_verificado_y_conserva_el_borrador_en_reintentos(self):
        kyc = self.verificacion(VerificacionKyc.Estado.VERIFICADO)
        self.assertEqual(self.generar().status_code, 409)  # Borrador creado, términos de firma deshabilitados.
        contrato = ContratoAdopcion.objects.get(solicitud=self.solicitud)
        self.assertEqual(contrato.snapshot["adoptanteNombre"], "Ana Pérez")
        self.assertEqual(contrato.snapshot["adoptanteDocumento"], "01234567")
        self.assertEqual(contrato.snapshot["adoptanteCorreo"], "titular@example.test")
        self.assertIn("01234567", contrato.contenido_html)

        kyc.dni = "11111111"
        kyc.save(update_fields=["dni"])
        self.assertEqual(self.generar().status_code, 409)
        contrato.refresh_from_db()
        self.assertEqual(contrato.snapshot["adoptanteDocumento"], "01234567")
        self.assertEqual(ContratoAdopcion.objects.count(), 1)
        self.assertEqual(SolicitudEvento.objects.filter(tipo=SolicitudEvento.Tipo.CONTRATO_GENERADO).count(), 1)

    def test_admite_revision_manual_aprobada(self):
        self.verificacion(VerificacionKyc.Estado.APROBADO_MANUAL)
        self.assertEqual(self.generar().status_code, 409)
        self.assertEqual(ContratoAdopcion.objects.get().snapshot["adoptanteDocumento"], "01234567")

    def test_admite_ficha_historica_sin_cuenta(self):
        self.solicitud.adoptante = None
        self.solicitud.postulante = Postulante.objects.create(
            documento_numero="CE1234", nombres="Rosa", apellidos="León", email="rosa@example.test"
        )
        self.solicitud.save(update_fields=["adoptante", "postulante"])
        self.assertEqual(self.generar().status_code, 409)
        datos = ContratoAdopcion.objects.get().snapshot
        self.assertEqual(datos["adoptanteNombre"], "Rosa León")
        self.assertEqual(datos["adoptanteDocumento"], "CE1234")
        self.assertEqual(datos["adoptanteCorreo"], "rosa@example.test")

    def test_sin_kyc_no_usa_el_documento_del_cuestionario_ni_de_una_ficha(self):
        self.solicitud.postulante = Postulante.objects.create(documento_numero="99999999")
        self.solicitud.save(update_fields=["postulante"])
        self.assert_falta("Falta el documento de identidad del titular")

    def test_kyc_sin_aprobar(self):
        self.verificacion(VerificacionKyc.Estado.RECHAZADO_MANUAL)
        self.assert_falta("requiere una verificación de identidad (KYC) aprobada")

    def test_kyc_sin_numero_de_documento(self):
        self.verificacion(VerificacionKyc.Estado.VERIFICADO, dni="")
        self.assert_falta("Falta el número de documento")

    def test_kyc_sin_apellidos(self):
        self.verificacion(VerificacionKyc.Estado.VERIFICADO, apellidos=" ")
        self.assert_falta("Faltan los nombres o apellidos")

    @override_settings(
        CONTRACT_SIGNATURE_API_URL="https://firma.example.test/contratos",
        CONTRACT_SIGNATURE_API_TOKEN="token-local-de-prueba",
    )
    def test_informa_si_falta_correo_y_el_adaptador_protege_borradores_anteriores(self):
        self.verificacion(VerificacionKyc.Estado.VERIFICADO)
        self.adoptante.email = ""
        self.adoptante.save(update_fields=["email"])
        self.assert_falta("Falta el correo electrónico del titular")

        contrato = ContratoAdopcion(solicitud=self.solicitud, snapshot={"adoptanteCorreo": ""})
        with patch("apps.contratos.providers.urlopen") as enviar:
            with self.assertRaisesMessage(ErrorProveedorFirma, "Falta el correo electrónico del titular"):
                ProveedorFirmaREST().enviar(contrato, "https://petly.example.test/callback")
            enviar.assert_not_called()
