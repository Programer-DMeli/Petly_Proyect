import time
import jwt
import urllib.error
from unittest.mock import patch
from django.conf import settings
from django.test import TestCase
from django.contrib.auth import get_user_model
from rest_framework.test import APIClient

class SpringIdentityTest(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.identity = {"id": "101", "correo": "shelter@example.test", "rol": "ALBERGUE"}
        self.claims = {"sub": "101", "rol": "ALBERGUE", "type": "access", "version": 0,
            "iss": "petly-spring", "aud": ["petly-django", "petly-api"], "iat": int(time.time()), "exp": int(time.time())+3600}
    def token(self, **changes):
        return jwt.encode(dict(self.claims, **changes), settings.SPRING_JWT_SECRET, algorithm="HS256")
    @patch("apps.acceso.authentication.spring_get")
    def test_valid_identity_has_no_local_password(self, authority):
        authority.return_value = self.identity
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.token())
        response = self.client.get("/api/albergues/")
        self.assertEqual(response.status_code, 200)
        user = get_user_model().objects.get(spring_id=101)
        self.assertFalse(user.has_usable_password())
        self.assertNotEqual(user.pk, 101)
    def test_expired_invalid_and_wrong_purpose_rejected(self):
        for token in ["broken", self.token(exp=int(time.time())-1), self.token(type="password_reset"), self.token(aud="other")]:
            self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
            self.assertEqual(self.client.get("/api/albergues/").status_code, 401)
        self.client.credentials()
        self.assertEqual(self.client.get("/api/albergues/").status_code, 401)
    @patch("apps.acceso.integracion.spring_get")
    @patch("apps.acceso.authentication.spring_get")
    def test_sharing_denied_after_revocation(self, authority, profiles):
        authority.return_value = self.identity
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.token())
        profiles.return_value = {"vivienda": "Departamento", "tieneNinos": False}
        self.assertEqual(self.client.get("/api/acceso/perfiles-compartidos/202/").status_code, 200)
        profiles.side_effect = urllib.error.HTTPError("local", 403, "forbidden", {}, None)
        self.assertEqual(self.client.get("/api/acceso/perfiles-compartidos/202/").status_code, 403)
    def test_retired_business_auth_not_available(self):
        for route in ["auth/registro/", "auth/login/", "interno/autorizacion/"]:
            self.assertEqual(self.client.post("/api/acceso/"+route, {}, format="json").status_code, 410)
        for route in ["postulantes/", "solicitudes/"]:
            self.assertEqual(self.client.post("/api/acceso/"+route, {}, format="json").status_code, 401)

    def test_business_staff_cannot_use_technical_login(self):
        from apps.acceso.forms import TechnicalAdminLoginForm
        from django.core.exceptions import ValidationError
        User = get_user_model()
        business = User.objects.create_user(username="business", password="private-test-only", is_staff=True, rol="ALBERGUE")
        with self.assertRaises(ValidationError):
            TechnicalAdminLoginForm().confirm_login_allowed(business)
        technical = User.objects.create_superuser(username="technical", password="private-test-only")
        TechnicalAdminLoginForm().confirm_login_allowed(technical)

    @patch("apps.acceso.integracion.spring_get")
    @patch("apps.acceso.authentication.spring_get")
    def test_lista_compartidos_y_rol_denegado(self, authority, profiles):
        authority.return_value = self.identity
        profiles.return_value = [{"adoptanteId":202,"correo":"authorized@example.test"}]
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.token())
        response = self.client.get('/api/acceso/perfiles-compartidos/')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data[0]['adoptanteId'], 202)
        authority.return_value = dict(self.identity, rol="ADOPTANTE")
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.token(rol="ADOPTANTE"))
        self.assertEqual(self.client.get('/api/acceso/perfiles-compartidos/').status_code, 403)
