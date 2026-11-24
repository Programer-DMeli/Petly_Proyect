"""URLs de albergues (US-46)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import AlbergueViewSet
from .chatbot import RespuestaAutomaticaChatbotViewSet
from .metricas import PermanenciaAlbergueView

router = DefaultRouter()
router.register(r"respuestas-chatbot", RespuestaAutomaticaChatbotViewSet, basename="respuesta-chatbot")
router.register(r"", AlbergueViewSet, basename="albergue")

urlpatterns = [
    path("mi-perfil/metricas/permanencia/", PermanenciaAlbergueView.as_view(), name="albergue-metrica-permanencia"),
    path("", include(router.urls)),
]
