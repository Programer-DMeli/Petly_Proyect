"""Valida Spring JWT y su vigencia contra la única autoridad de cuentas."""
import json
import urllib.request
import urllib.error
import jwt
from django.conf import settings
from django.contrib.auth import get_user_model
from rest_framework.authentication import BaseAuthentication, get_authorization_header
from rest_framework.exceptions import AuthenticationFailed, APIException

class AuthorityUnavailable(APIException):
    status_code = 503
    default_detail = "La autoridad de acceso no está disponible."


def spring_get(path, token):
    req = urllib.request.Request(settings.SPRING_API_URL + path, headers={"Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 401:
            raise AuthenticationFailed("Token inválido o vencido.")
        raise
    except (urllib.error.URLError, TimeoutError):
        raise AuthorityUnavailable()


class SpringBootJWTAuthentication(BaseAuthentication):
    def authenticate_header(self, request):
        return "Bearer"

    def authenticate(self, request):
        header = get_authorization_header(request).split()
        if not header:
            return None
        if len(header) != 2 or header[0].lower() != b"bearer":
            raise AuthenticationFailed("Cabecera de acceso inválida.")
        try:
            token = header[1].decode("ascii")
            claims = jwt.decode(token, settings.SPRING_JWT_SECRET, algorithms=["HS256"],
                issuer="petly-spring", audience="petly-django", options={"require": ["sub", "exp", "iat", "rol", "type", "version"]})
            if claims["type"] != "access" or claims["rol"] not in {"ADOPTANTE", "ALBERGUE", "ADMIN"}:
                raise ValueError()
            uid = int(claims["sub"])
            if uid < 1:
                raise ValueError()
        except (jwt.PyJWTError, ValueError, UnicodeError):
            raise AuthenticationFailed("Token inválido o vencido.")
        # No confiar solo en firma: comprueba revocación por cambio de contraseña.
        try:
            identity = spring_get("/api/auth/me", token)
        except urllib.error.HTTPError:
            raise AuthorityUnavailable()
        if str(identity["id"]) != str(uid) or identity["rol"] != claims["rol"]:
            raise AuthenticationFailed("Identidad inválida.")
        User = get_user_model()
        user, created = User.objects.get_or_create(spring_id=uid, defaults={
            "username": "spring_" + str(uid), "email": identity["correo"], "rol": identity["rol"]})
        if not user.is_active:
            raise AuthenticationFailed("Cuenta deshabilitada.")
        user.email = identity["correo"]
        user.rol = identity["rol"]
        user.set_unusable_password()
        user.save(update_fields=["email", "rol", "password"])
        return user, token
