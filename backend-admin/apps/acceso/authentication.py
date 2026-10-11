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


class DualJWTAuthentication(JWTAuthentication):
    """
    Autenticación que intenta primero validar token Spring Boot,
    si falla, valida token Django (para adoptante móvil).
    """
    
    def authenticate(self, request):
        # Intentar autenticación estándar (Django tokens para adoptante)
        try:
            return super().authenticate(request)
        except (InvalidToken, TokenError):
            pass
        
        # Si falla, intentar Spring Boot token
        spring_auth = SpringBootJWTAuthentication()
        try:
            return spring_auth.authenticate(request)
        except (InvalidToken, TokenError):
            return None