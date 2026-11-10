from datetime import date, timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from rest_framework.test import APIClient

from apps.albergues.models import Albergue, MetricaRespuestaChatbot, RespuestaAutomaticaChatbot


User = get_user_model()


class ChatbotMetricasTest(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(username="chat_shelter", password="pass", rol="ALBERGUE")
        self.other_owner = User.objects.create_user(username="other_chat_shelter", password="pass", rol="ALBERGUE")
        self.adopter = User.objects.create_user(username="chat_adopter", password="pass", rol="ADOPTANTE")
        self.shelter = Albergue.objects.create(usuario=self.owner, nombre="Patitas", direccion="Lima")
        self.other_shelter = Albergue.objects.create(usuario=self.other_owner, nombre="Huellitas", direccion="Lima")
        self.client = APIClient()

    def test_metricas_solo_cuentan_el_albergue_y_periodo_solicitados(self):
        hoy = date.today()
        MetricaRespuestaChatbot.objects.create(
            albergue=self.shelter, pregunta_clave="Horario", fecha=hoy, consultas=4,
        )
        MetricaRespuestaChatbot.objects.create(
            albergue=self.shelter, pregunta_clave="Visitas", fecha=hoy - timedelta(days=40), consultas=7,
        )
        MetricaRespuestaChatbot.objects.create(
            albergue=self.other_shelter, pregunta_clave="Horario", fecha=hoy, consultas=20,
        )
        self.client.force_authenticate(self.owner)

        response = self.client.get(
            "/api/albergues/respuestas-chatbot/metricas/",
            {"desde": hoy.isoformat(), "hasta": hoy.isoformat()},
        )

        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["total_consultas"], 4)
        self.assertEqual(response.data["preguntas"], [{"pregunta_clave": "Horario", "consultas": 4}])

    def test_adoptante_no_puede_consultar_metricas_del_albergue(self):
        self.client.force_authenticate(self.adopter)

        response = self.client.get("/api/albergues/respuestas-chatbot/metricas/")

        self.assertEqual(response.status_code, 403)

    def test_respuesta_coincidente_registra_conteo_agregado_del_albergue(self):
        rule = RespuestaAutomaticaChatbot.objects.create(
            albergue=self.shelter,
            pregunta_clave="horario de atención",
            respuesta="Atendemos de lunes a viernes.",
        )
        self.client.force_authenticate(self.adopter)

        response = self.client.post(
            "/api/albergues/respuestas-chatbot/coincidencia/",
            {"pregunta": "¿Cuál es el HORARIO DE ATENCIÓN?"},
            format="json",
        )

        self.assertEqual(response.status_code, 200, response.data)
        self.assertEqual(response.data["respuesta"], rule.respuesta)
        metric = MetricaRespuestaChatbot.objects.get(albergue=self.shelter, pregunta_clave=rule.pregunta_clave)
        self.assertEqual(metric.consultas, 1)
