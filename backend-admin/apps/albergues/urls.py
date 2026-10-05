"""URLs de albergues (US-46)."""
from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import AlbergueViewSet

router = DefaultRouter()
router.register(r"", AlbergueViewSet, basename="albergue")

urlpatterns = [
    path("", include(router.urls)),
]