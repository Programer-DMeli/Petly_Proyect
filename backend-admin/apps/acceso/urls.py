from django.urls import path, re_path
from .integracion import PerfilCompartidoView, RetiredBusinessAccessView
urlpatterns = [
    path("perfiles-compartidos/<int:adoptante_id>/", PerfilCompartidoView.as_view(), name="perfil_compartido"),
    # Rutas antiguas no exponen credenciales, postulantes ni solicitudes del siguiente sprint.
    re_path(r"^.*$", RetiredBusinessAccessView.as_view()),
]
