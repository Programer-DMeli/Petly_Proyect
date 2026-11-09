from django.urls import path
from rest_framework.routers import DefaultRouter
from .views import AdjuntoView, BienvenidaView, ConversacionViewSet

router = DefaultRouter()
router.register("conversaciones", ConversacionViewSet, basename="conversacion")

urlpatterns = [
    path("bienvenida/", BienvenidaView.as_view(), name="bienvenida-chat"),
    path("adjuntos/<int:pk>/", AdjuntoView.as_view(), name="adjunto-chat"),
] + router.urls
