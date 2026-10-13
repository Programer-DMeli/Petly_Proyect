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
        jwt_secret = getattr(settings, 'JWT_SECRET', None) or getattr(settings, 'SIMPLE_JWT', {}).get('VERIFYING_KEY')
        if not jwt_secret:
            raise InvalidToken("Clave de verificación Spring Boot no configurada")
        
        # Usar la clave de Spring Boot para validar
        from rest_framework_simplejwt.tokens import AccessToken
        token = AccessToken(raw_token, verifying_key=jwt_secret)
        
        # Verificar issuer y audience de Spring Boot
        if token.get("iss") != "petly-spring":
            raise InvalidToken("Issuer inválido para token Spring Boot")
        if token.get("aud") != "petly-django":
            raise InvalidToken("Audience inválido para token Spring Boot")
        
        return token
    
    def get_user(self, validated_token):
        """Obtener/crear usuario local desde claims de Spring Boot."""
        try:
            user_id = validated_token.get("sub")
            email = validated_token.get("email", "")
            rol = validated_token.get("rol", "ADOPTANTE")
            
            if not user_id:
                raise InvalidToken("Token sin claim 'sub' (user_id)")
            
            # Get or create local User para permisos Django
            user, created = User.objects.get_or_create(
                id=user_id,
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