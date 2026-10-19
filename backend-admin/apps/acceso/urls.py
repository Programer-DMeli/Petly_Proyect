from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import AdoptanteProfileView, PostulanteViewSet, SolicitudAdopcionViewSet
from .integracion import PerfilCompartidoView, PerfilesCompartidosListView, RetiredBusinessAccessView

router = DefaultRouter()
router.register("postulantes", PostulanteViewSet, basename="postulante")
router.register("solicitudes", SolicitudAdopcionViewSet, basename="solicitud")

urlpatterns = [
    path("", include(router.urls)),
    path("mi-perfil/", AdoptanteProfileView.as_view(), name="mi_perfil_adoptante"),
    path("perfiles-compartidos/", PerfilesCompartidosListView.as_view(), name="perfiles_compartidos"),
    path("perfiles-compartidos/<int:adoptante_id>/", PerfilCompartidoView.as_view(), name="perfil_compartido"),
    path("<path:unused>", RetiredBusinessAccessView.as_view()),
]
