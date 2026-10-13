"""Autenticación JWT para validar tokens de Spring Boot (US-21)."""
from rest_framework_simplejwt.authentication import JWTAuthentication
from rest_framework_simplejwt.exceptions import InvalidToken, TokenError
from django.contrib.auth import get_user_model

User = get_user_model()


class SpringBootJWTAuthentication(JWTAuthentication):
    """
    Autenticación que valida tokens emitidos por Spring Boot.
    
    Spring Boot claims: sub=id, email, rol, iss=petly-spring, aud=petly-django
    Crea/actualiza User local para permisos Django sin duplicar contraseñas.
    """
    
    def get_validated_token(self, raw_token):
        """Validar token con clave de Spring Boot y verificar iss/aud."""
        from django.conf import settings
        from rest_framework_simplejwt.tokens import AccessToken
        from rest_framework_simplejwt.backends import TokenBackend
        from rest_framework_simplejwt.settings import api_settings
        import time
        
        jwt_secret = getattr(settings, 'SPRING_JWT_SECRET', None) or getattr(settings, 'JWT_SECRET', None) or getattr(settings, 'SIMPLE_JWT', {}).get('VERIFYING_KEY')
        if not jwt_secret:
            raise InvalidToken("Clave de verificación Spring Boot no configurada")
        
        # Usar TokenBackend directamente con la clave de Spring Boot
        # Verificar expiration, issuer y audience
        token_backend = TokenBackend(
            algorithm=api_settings.ALGORITHM,
            signing_key=jwt_secret,
            verifying_key=jwt_secret,
            audience="petly-django",  # Verificar audience en decode
            issuer="petly-spring",    # Verificar issuer en decode
            leeway=api_settings.LEEWAY,
        )
        
        try:
            payload = token_backend.decode(raw_token, verify=True)
        except Exception as e:
            raise InvalidToken(f"Token inválido: {str(e)}")
        
        # Verificar expiration manualmente (por si TokenBackend no lo hace)
        exp = payload.get("exp")
        if exp is not None:
            if exp < int(time.time()):
                raise InvalidToken("Token expirado")
        
        # Verificar audience manualmente (TokenBackend solo verifica string, no lista)
        aud = payload.get("aud")
        if isinstance(aud, list):
            if "petly-django" not in aud:
                raise InvalidToken("Audience inválido para token Spring Boot")
        elif aud != "petly-django":
            raise InvalidToken("Audience inválido para token Spring Boot")
        
        # Verificar tipo de token (debe ser "access" para endpoints de API)
        token_type = payload.get("type")
        if token_type != "access":
            raise InvalidToken(f"Tipo de token inválido: se esperaba 'access', recibido '{token_type}'")
        
        # Crear AccessToken con el payload validado
        token = AccessToken()
        token.payload = payload
        return token
    
    def get_user(self, validated_token):
        """Obtener/crear usuario local desde claims de Spring Boot."""
        try:
            user_id = validated_token.get("sub")
            email = validated_token.get("email", "")
            rol = validated_token.get("rol", "ADOPTANTE")
            
            if not user_id:
                raise InvalidToken("Token sin claim 'sub' (user_id)")
            
            # Get or create local User para permisos Django usando spring_id
            user, created = User.objects.get_or_create(
                spring_id=user_id,
                defaults={
                    "username": email or f"user_{user_id}",
                    "email": email,
                    "rol": rol,
                    "is_active": True,
                }
            )
            
            # Actualizar rol si cambió
            if not created and user.rol != rol:
                user.rol = rol
                user.save(update_fields=["rol"])
            
            # Usuario de Spring Boot no debe tener contraseña usable localmente
            if created or user.has_usable_password():
                user.set_unusable_password()
                user.save(update_fields=["password"])
            
            return user
            
        except User.DoesNotExist:
            raise InvalidToken("Usuario no encontrado")
        except Exception as e:
            raise InvalidToken(f"Error validando token: {str(e)}")


class DjangoJWTAuthentication(JWTAuthentication):
    """
    Autenticación para tokens emitidos por Django (adoptante móvil).
    Valida issuer=petly-django, audience=petly-mobile.
    """
    
    def get_validated_token(self, raw_token):
        token = super().get_validated_token(raw_token)
        
        # Verificar issuer y audience de Django
        if token.get("iss") != "petly-django":
            raise InvalidToken("Issuer inválido para token Django")
        if token.get("aud") != "petly-mobile":
            raise InvalidToken("Audience inválido para token Django")
        
        return token


class DualJWTAuthentication(JWTAuthentication):
    """
    Autenticación que intenta:
    1. Token Django (adoptante móvil): iss=petly-django, aud=petly-mobile
    2. Token Spring Boot (admin/albergue): iss=petly-spring, aud=petly-django
    """
    
    def authenticate(self, request):
        # 1. Intentar token Django (adoptante móvil)
        try:
            django_auth = DjangoJWTAuthentication()
            return django_auth.authenticate(request)
        except (InvalidToken, TokenError):
            pass
        
        # 2. Intentar token Spring Boot (admin/albergue)
        try:
            spring_auth = SpringBootJWTAuthentication()
            return spring_auth.authenticate(request)
        except (InvalidToken, TokenError):
            return None


# --- Spring Boot integration (US-18, US-46) ---
import urllib.request
import urllib.error
import json
from django.conf import settings


class AuthorityUnavailable(Exception):
    """Spring Boot no responde o devuelve error."""
    pass


def spring_get(path: str, auth_header: str | None = None, timeout: float = 3.0):
    """
    Llama a Spring Boot (GET) y devuelve JSON decodificado.
    Lanza AuthorityUnavailable si no hay respuesta 2xx.
    """
    base = getattr(settings, "SPRING_BOOT_BASE_URL", "http://localhost:8080").rstrip("/")
    url = f"{base}{path}"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    if auth_header:
        req.add_header("Authorization", auth_header)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            if 200 <= resp.status < 300:
                data = resp.read().decode("utf-8")
                return json.loads(data) if data else {}
            raise AuthorityUnavailable(f"Spring {resp.status}: {resp.read().decode()}")
    except urllib.error.HTTPError as e:
        raise AuthorityUnavailable(f"Spring HTTP {e.code}: {e.read().decode()}")
    except Exception as e:
        raise AuthorityUnavailable(f"Spring inalcanzable: {e}")
