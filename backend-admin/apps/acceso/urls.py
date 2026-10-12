from django.urls import path, re_path
from .integracion import PerfilCompartidoView, PerfilesCompartidosListView, RetiredBusinessAccessView
urlpatterns = [
    path("perfiles-compartidos/", PerfilesCompartidosListView.as_view(), name="perfiles_compartidos"),
    path("perfiles-compartidos/<int:adoptante_id>/", PerfilCompartidoView.as_view(), name="perfil_compartido"),
    # Rutas antiguas no exponen credenciales, postulantes ni solicitudes del siguiente sprint.
    re_path(r"^.*$", RetiredBusinessAccessView.as_view()),
]
