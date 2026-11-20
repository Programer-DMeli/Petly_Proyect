from datetime import date, timedelta
from io import BytesIO

import jwt
from django.conf import settings
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from pypdf import PdfReader
from rest_framework import status
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota, RegistroClinico


class FichaVeterinariaPDFTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.titular = usuarios.create_user(username="ficha-titular", rol="ADOPTANTE", usuario_spring_id=17057)
        cls.otro_adoptante = usuarios.create_user(username="ficha-otro", rol="ADOPTANTE")
        cls.responsable = usuarios.create_user(username="ficha-albergue", rol="ALBERGUE")
        cls.admin = usuarios.create_user(username="ficha-admin", rol="ADMIN", is_staff=True)
        albergue = Albergue.objects.create(usuario=cls.responsable, nombre="Refugio", direccion="Lima")
        cls.mascota = Mascota.objects.create(
            albergue=albergue, nombre="Núñez & Luna", codigo="VET-057", especie="GATO",
            sexo="HEMBRA", raza="Mestiza", vacunas_al_dia=True, esterilizado=True,
            estado_salud_general="Control de rutina.", necesidades_especiales="Dieta blanda.",
        )
        cls.solicitud = SolicitudAdopcion.objects.create(
            mascota=cls.mascota, albergue=albergue, adoptante=cls.titular,
            estado=SolicitudAdopcion.Estado.ENTREGADA, fecha_entrega=date(2026, 11, 12),
            ficha_veterinaria_revisada={"estado_salud_general": "Observación anterior a la entrega"},
        )

    def setUp(self):
        self.client.force_authenticate(self.titular)
        self.url = reverse("solicitud-ficha-veterinaria", args=[self.solicitud.pk])

    def descargar_paginas(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response["Content-Type"], "application/pdf")
        self.assertEqual(response["Cache-Control"], "private, no-store")
        self.assertEqual(
            response["Content-Disposition"], f'attachment; filename="ficha_veterinaria_solicitud_{self.solicitud.pk}.pdf"',
        )
        archivo = b"".join(response.streaming_content)
        self.assertTrue(archivo.startswith(b"%PDF-"))
        return [pagina.extract_text() for pagina in PdfReader(BytesIO(archivo), strict=True).pages]

    def test_descarga_la_ficha_vigente_con_los_tres_procedimientos(self):
        Mascota.objects.filter(pk=self.mascota.pk).update(estado_salud_general="Control vigente <sin fiebre>.")
        for tipo, dia in zip(RegistroClinico.Tipo.values, [7, 8, 9]):
            self.mascota.registros_clinicos.create(tipo=tipo, fecha=date(2026, 11, dia))

        token = jwt.encode({
            "sub": str(self.titular.usuario_spring_id), "rol": "ADOPTANTE", "type": "access",
            "iss": "petly-spring", "aud": "petly-django", "exp": timezone.now() + timedelta(minutes=5),
        }, settings.SPRING_JWT_SECRET, algorithm="HS256")
        self.client.force_authenticate(None)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")
        contenido = "\n".join(self.descargar_paginas())
        for texto in (
            "Núñez & Luna", "VET-057", "Gato", "Mestiza", "Hembra",
            "Vacunas al día: Sí", "Esterilización: Sí", "Desparasitación: No",
            "Control vigente <sin fiebre>.", "Dieta blanda.",
            "Vacunación\n07/11/2026", "Esterilización\n08/11/2026", "Desparasitación\n09/11/2026",
        ):
            self.assertIn(texto, contenido)
        self.assertNotIn("Observación anterior a la entrega", contenido)

    def test_descarga_ficha_antigua_sin_inventar_fechas(self):
        contenido = "\n".join(self.descargar_paginas())
        self.assertIn("No hay registros clínicos con fecha.", contenido)
        self.assertIn("Vacunas al día: Sí", contenido)
        self.assertNotRegex(contenido, r"\b\d{2}/\d{2}/\d{4}\b")

    def test_historial_largo_conserva_todas_las_fechas_y_repite_las_columnas(self):
        fechas = [date(2026, 11, 1) - timedelta(days=dias) for dias in range(95)]
        RegistroClinico.objects.bulk_create([
            RegistroClinico(mascota=self.mascota, tipo=RegistroClinico.Tipo.VACUNACION, fecha=fecha)
            for fecha in fechas
        ])
        paginas = self.descargar_paginas()
        self.assertGreater(len(paginas), 1)
        for numero, pagina in enumerate(paginas, start=1):
            self.assertIn(f"Página {numero}", pagina)
            self.assertIn("Procedimiento\nFecha", pagina)
        for fecha in fechas:
            self.assertEqual("\n".join(paginas).count(fecha.strftime("%d/%m/%Y")), 1)

    def test_observaciones_largas_continuan_en_otras_paginas(self):
        lineas = [f"Nota clínica {numero:02d}." for numero in range(65)]
        Mascota.objects.filter(pk=self.mascota.pk).update(estado_salud_general="\n".join(lineas))
        paginas = self.descargar_paginas()
        self.assertGreater(len(paginas), 1)
        contenido = "\n".join(paginas)
        for linea in lineas:
            self.assertEqual(contenido.count(linea), 1)

    def test_rechaza_cualquier_estado_distinto_de_entregada(self):
        for estado in SolicitudAdopcion.Estado.values:
            if estado == SolicitudAdopcion.Estado.ENTREGADA:
                continue
            with self.subTest(estado=estado):
                SolicitudAdopcion.objects.filter(pk=self.solicitud.pk).update(estado=estado, fecha_entrega=None)
                response = self.client.get(self.url)
                self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)
                self.assertIn("cuando se confirme la entrega", response.data["detail"])

    def test_rechaza_terceros_y_solicitudes_inexistentes(self):
        for identificador in (self.solicitud.pk + 1, "invalida"):
            url_inexistente = reverse("solicitud-ficha-veterinaria", args=[identificador])
            self.assertEqual(self.client.get(url_inexistente).status_code, status.HTTP_404_NOT_FOUND)
        for usuario, codigo in (
            (self.otro_adoptante, status.HTTP_404_NOT_FOUND),
            (self.responsable, status.HTTP_403_FORBIDDEN),
            (self.admin, status.HTTP_403_FORBIDDEN),
            (None, status.HTTP_401_UNAUTHORIZED),
        ):
            with self.subTest(usuario=usuario):
                self.client.force_authenticate(usuario)
                self.assertEqual(self.client.get(self.url).status_code, codigo)
