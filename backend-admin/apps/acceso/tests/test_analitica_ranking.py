from datetime import date
from io import BytesIO

from django.contrib.auth import get_user_model
from django.test import override_settings
from django.urls import reverse
from django.utils import timezone
from openpyxl import load_workbook
from pypdf import PdfReader
from rest_framework.test import APITestCase

from apps.acceso.models import SolicitudAdopcion
from apps.albergues.models import Albergue
from apps.mascotas.models import Mascota


@override_settings(CACHES={"default": {"BACKEND": "django.core.cache.backends.locmem.LocMemCache"}})
class RankingAdopcionesTest(APITestCase):
    @classmethod
    def setUpTestData(cls):
        usuarios = get_user_model().objects
        cls.admin = usuarios.create_user(username="ranking-admin", rol="ADMIN")
        cls.responsable = usuarios.create_user(username="ranking-albergue", rol="ALBERGUE")
        cls.adoptante = usuarios.create_user(username="ranking-adoptante", rol="ADOPTANTE")
        cls.albergue = Albergue.objects.create(usuario=cls.responsable, nombre="Refugio", direccion="Lima")

    def setUp(self):
        self.client.force_authenticate(self.admin)
        self.url = reverse("analitica_adopciones_ranking")
        self.filtro = {"desde": "2026-01-01", "hasta": "2026-01-10"}

    def mascota(self, codigo, especie, ingreso, raza=""):
        mascota = Mascota.objects.create(
            albergue=self.albergue, nombre=codigo, codigo=codigo, especie=especie, sexo="HEMBRA", raza=raza,
        )
        Mascota.objects.filter(pk=mascota.pk).update(fecha_ingreso=ingreso)
        return mascota

    def solicitud(self, mascota, entrega, estado="ENTREGADA"):
        return SolicitudAdopcion.objects.create(
            albergue=self.albergue, mascota=mascota, estado=estado, fecha_entrega=entrega,
        )

    def ranking(self, **filtros):
        respuesta = self.client.get(self.url, filtros)
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        return respuesta.data

    def test_ordena_por_tasa_y_aplica_cohorte_primera_entrega_y_permanencia(self):
        gatos = [self.mascota(f"G-{dia}", "GATO", date(2026, 1, dia)) for dia in (1, 2, 3, 10)]
        perros = [self.mascota(f"P-{dia}", "PERRO", date(2026, 1, dia)) for dia in (1, 2, 5, 10)]
        anterior = self.mascota("G-ANTERIOR", "GATO", date(2025, 12, 31))
        self.mascota("P-POSTERIOR", "PERRO", date(2026, 1, 11))
        self.solicitud(anterior, date(2026, 1, 1))
        self.solicitud(gatos[0], date(2025, 12, 31))  # Anterior al ingreso.
        self.solicitud(gatos[0], date(2026, 1, 5))  # 4 días.
        self.solicitud(gatos[0], date(2026, 1, 8))  # La primera válida tiene prioridad.
        self.solicitud(gatos[1], date(2026, 1, 6), estado="APROBADA")
        self.solicitud(gatos[2], date(2026, 1, 11))  # Posterior al corte.
        self.solicitud(perros[0], date(2026, 1, 2))  # 1 día.
        self.solicitud(perros[1], date(2026, 1, 10))  # 8 días.
        self.solicitud(perros[2], None)

        datos = self.ranking(**self.filtro)
        self.assertEqual(datos["agrupacion"], "especie")
        self.assertEqual((datos["desde"], datos["hasta"]), ("2026-01-01", "2026-01-10"))
        self.assertEqual([
            (grupo["especie"], grupo["mascotas_ingresadas"], grupo["mascotas_entregadas"],
             grupo["tasa_adopcion"], grupo["dias_promedio"])
            for grupo in datos["grupos"]
        ], [("GATO", 4, 1, 25, 4), ("PERRO", 4, 2, 50, 4.5)])
        self.assertEqual(datos["totales"], {
            "mascotas_ingresadas": 8, "mascotas_entregadas": 3, "tasa_adopcion": 37.5, "dias_promedio": 4.3,
        })

        otro_periodo = self.ranking(desde="2026-01-02", hasta="2026-01-03")
        self.assertEqual(otro_periodo["totales"]["mascotas_ingresadas"], 3)
        self.assertEqual(otro_periodo["totales"]["tasa_adopcion"], 0)
        self.assertIsNone(otro_periodo["totales"]["dias_promedio"])

    def test_separa_razas_por_especie_y_ordena_empates_con_muestras_pequenas(self):
        for codigo, especie, raza in (
            ("G1", "GATO", "Común"), ("G2", "GATO", " común  "), ("G3", "GATO", " "),
            ("P1", "PERRO", "Común"), ("P2", "PERRO", "Labrador"),
        ):
            self.mascota(codigo, especie, date(2026, 1, 1), raza)

        datos = self.ranking(agrupacion="raza", **self.filtro)
        self.assertEqual([
            (fila["especie"], fila["raza"], fila["mascotas_ingresadas"]) for fila in datos["grupos"]
        ], [("GATO", None, 1), ("GATO", "Común", 2), ("PERRO", "Común", 1), ("PERRO", "Labrador", 1)])
        for fila in datos["grupos"]:
            self.assertEqual(fila["tasa_adopcion"], 0)
            self.assertIsNone(fila["dias_promedio"])

    def test_sin_ingresos_no_hay_tasa_y_sin_hasta_se_usa_hoy(self):
        datos = self.ranking(**self.filtro)
        self.assertEqual(datos["grupos"], [])
        self.assertEqual(datos["totales"], {
            "mascotas_ingresadas": 0, "mascotas_entregadas": 0, "tasa_adopcion": None, "dias_promedio": None,
        })
        self.assertEqual(self.ranking()["hasta"], timezone.localdate().isoformat())

    def test_valida_filtros_y_restringe_acceso_a_admin(self):
        for filtro in ({"desde": "ayer"}, {"desde": "2026-01-11", "hasta": "2026-01-10"}, {"agrupacion": "refugio"}):
            with self.subTest(filtro=filtro):
                self.assertEqual(self.client.get(self.url, filtro).status_code, 400)
        for actor, codigo in ((self.responsable, 403), (self.adoptante, 403), (None, 401)):
            with self.subTest(actor=actor):
                self.client.force_authenticate(actor)
                self.assertEqual(self.client.get(self.url).status_code, codigo)

    def test_regresion_analitica_y_exportaciones_se_filtran_por_entrega(self):
        anterior = self.mascota("P-ANTERIOR", "PERRO", date(2025, 12, 31))
        pendiente = self.mascota("G-PENDIENTE", "GATO", date(2026, 1, 1))
        self.solicitud(anterior, date(2026, 1, 1))
        self.solicitud(pendiente, date(2026, 1, 11))
        self.assertEqual(self.ranking(**self.filtro)["totales"]["mascotas_entregadas"], 0)

        resumen = self.client.get(reverse("analitica_adopciones"), self.filtro)
        self.assertEqual(resumen.status_code, 200)
        self.assertEqual(resumen.data["total_adopciones"], 1)
        self.assertEqual(resumen.data["por_mes"], [{"anio": 2026, "mes": 1, "total": 1}])

        exportar = reverse("analitica_adopciones_exportar")
        xlsx = self.client.get(exportar, {**self.filtro, "formato": "xlsx"})
        self.assertEqual(xlsx.status_code, 200)
        libro = load_workbook(BytesIO(xlsx.content), read_only=True)
        self.addCleanup(libro.close)
        self.assertEqual(libro["Resumen"]["B4"].value, 1)
        self.assertEqual(libro["Por mes"]["C2"].value, 1)

        pdf = self.client.get(exportar, {**self.filtro, "formato": "pdf"})
        self.assertEqual(pdf.status_code, 200)
        texto = "\n".join(pagina.extract_text() for pagina in PdfReader(BytesIO(pdf.content)).pages)
        self.assertIn("Periodo de entrega: 01/01/2026 al 10/01/2026", texto)
        self.assertIn("Total de adopciones entregadas: 1", texto)
