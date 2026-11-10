"""URLs de albergues (US-46)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import AlbergueViewSet
from .chatbot import RespuestaAutomaticaChatbotViewSet
from .mensajeria import ConversacionesView, MensajesConversacionView

router = DefaultRouter()
router.register(r"respuestas-chatbot", RespuestaAutomaticaChatbotViewSet, basename="respuesta-chatbot")
router.register(r"", AlbergueViewSet, basename="albergue")

urlpatterns = [
    path("conversaciones/", ConversacionesView.as_view(), name="conversaciones-adopcion"),
    path("conversaciones/<int:pk>/mensajes/", MensajesConversacionView.as_view(), name="mensajes-conversacion"),
    path("", include(router.urls)),
]
