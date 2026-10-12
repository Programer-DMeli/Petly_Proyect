"""Acceso técnico interno; las cuentas de negocio solo acceden con JWT Spring."""
from django.contrib.admin.forms import AdminAuthenticationForm
from django.core.exceptions import ValidationError

class TechnicalAdminLoginForm(AdminAuthenticationForm):
    def confirm_login_allowed(self, user):
        super().confirm_login_allowed(user)
        if not user.is_superuser or user.spring_id is not None:
            raise ValidationError("Utiliza el acceso de Petly para cuentas de negocio.", code="invalid_login")
