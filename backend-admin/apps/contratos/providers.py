"""Adaptador REST neutro; se puede sustituir por un proveedor con otro contrato API."""
import json
import ipaddress
import hashlib
import hmac
import secrets
from datetime import timedelta
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from django.conf import settings
from django.core.mail import EmailMultiAlternatives
from django.db import transaction
from django.utils import timezone
from django.utils.html import escape, strip_tags
from django.utils.module_loading import import_string


class ErrorProveedorFirma(Exception):
    pass


class ProveedorNoConfigurado(ErrorProveedorFirma):
    pass


def obtener_callback_firma():
    """Valida que el webhook use una dirección pública alcanzable por el proveedor."""
    base = settings.PUBLIC_API_BASE_URL.strip().rstrip("/")
    parsed = urlsplit(base)
    host = (parsed.hostname or "").lower().rstrip(".")
    local = host in {"localhost", "localhost.localdomain"} or host.endswith((".localhost", ".local", ".test", ".example"))
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        address = None
    local = local or (address is not None and not address.is_global)

    if not host or parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ProveedorNoConfigurado("PUBLIC_API_BASE_URL debe ser una URL base válida y sin credenciales.")
    if settings.DEBUG and parsed.scheme == "http" and local:
        return f"{base}/api/contratos/webhooks/firma/"
    if parsed.scheme != "https" or local:
        raise ProveedorNoConfigurado(
            "PUBLIC_API_BASE_URL debe ser HTTPS y públicamente alcanzable para recibir el webhook de firma."
        )
    return f"{base}/api/contratos/webhooks/firma/"


class ProveedorFirmaREST:
    """Protocolo genérico esperado: POST con signers/document y respuesta id/signingUrl."""

    def enviar(self, contrato, callback_url):
        endpoint = settings.CONTRACT_SIGNATURE_API_URL
        token = settings.CONTRACT_SIGNATURE_API_TOKEN
        if not endpoint or not token:
            raise ProveedorNoConfigurado("Configura CONTRACT_SIGNATURE_API_URL y CONTRACT_SIGNATURE_API_TOKEN.")
        endpoint_parts = urlsplit(endpoint)
        esquemas_endpoint = {"https"} | ({"http"} if settings.DEBUG else set())
        if endpoint_parts.scheme not in esquemas_endpoint or not endpoint_parts.netloc:
            raise ProveedorNoConfigurado("El endpoint del proveedor debe usar HTTPS (HTTP solo en DEBUG).")

        solicitud = contrato.solicitud
        email_adoptante = (contrato.snapshot.get("adoptanteCorreo") or solicitud.postulante.email).strip()
        email_albergue = contrato.snapshot.get("albergueCorreo", "").strip()
        if not email_adoptante or not email_albergue:
            raise ErrorProveedorFirma("El adoptante y el albergue deben tener correo para iniciar la firma.")

        payload = {
            "reference": str(contrato.public_id),
            "title": f"Contrato de adopción {contrato.snapshot['contratoNumero']}",
            "document": {"format": "html", "sha256": contrato.contenido_sha256, "content": contrato.contenido_html},
            "signers": [
                {"role": "adoptante", "name": contrato.snapshot["adoptanteNombre"], "email": email_adoptante},
                {"role": "albergue", "name": contrato.snapshot["albergueNombre"], "email": email_albergue},
            ],
            "webhookUrl": callback_url,
        }
        req = Request(
            endpoint,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
                "Idempotency-Key": f"petly-{contrato.public_id}-{contrato.contenido_sha256[:16]}",
            },
            method="POST",
        )
        try:
            with urlopen(req, timeout=settings.CONTRACT_SIGNATURE_TIMEOUT_SECONDS) as response:
                body = response.read(1_000_001)
                if len(body) > 1_000_000:
                    raise ErrorProveedorFirma("La respuesta del proveedor superó el límite permitido.")
                result = json.loads(body.decode("utf-8"))
        except HTTPError as exc:
            raise ErrorProveedorFirma(f"El proveedor respondió HTTP {exc.code}.") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise ErrorProveedorFirma("No fue posible conectar con el proveedor de firma.") from exc
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ErrorProveedorFirma("El proveedor devolvió una respuesta no válida.") from exc

        provider_id = result.get("id") or result.get("documentId")
        signing_url = result.get("signingUrl") or result.get("url") or ""
        if not provider_id:
            raise ErrorProveedorFirma("La respuesta del proveedor no incluye id del documento.")
        if signing_url:
            parsed_url = urlsplit(str(signing_url))
            esquemas_validos = {"https"} | ({"http"} if settings.DEBUG else set())
            if parsed_url.scheme not in esquemas_validos or not parsed_url.netloc:
                raise ErrorProveedorFirma("El proveedor devolvió una URL de firma no segura.")
        return {
            "id": str(provider_id),
            "url": str(signing_url),
            "status": str(result.get("status", "sent")).lower(),
        }

    def descargar(self, contrato):
        """Descarga el PDF final únicamente después de la confirmación de firmas."""
        if not settings.CONTRACT_SIGNATURE_API_URL or not settings.CONTRACT_SIGNATURE_API_TOKEN:
            raise ProveedorNoConfigurado("Configura el proveedor de firma antes de descargar el contrato.")
        endpoint = settings.CONTRACT_SIGNATURE_API_URL.rstrip("/") + "/" + str(contrato.proveedor_documento_id) + "/download"
        parts = urlsplit(endpoint)
        esquemas = {"https"} | ({"http"} if settings.DEBUG else set())
        if parts.scheme not in esquemas or not parts.netloc:
            raise ProveedorNoConfigurado("La descarga del contrato debe usar HTTPS (HTTP solo en DEBUG).")
        req = Request(endpoint, headers={
            "Authorization": f"Bearer {settings.CONTRACT_SIGNATURE_API_TOKEN}",
            "Accept": "application/pdf",
        }, method="GET")
        try:
            with urlopen(req, timeout=settings.CONTRACT_SIGNATURE_TIMEOUT_SECONDS) as response:
                data = response.read(20 * 1024 * 1024 + 1)
        except HTTPError as exc:
            raise ErrorProveedorFirma(f"No se pudo descargar el contrato firmado (HTTP {exc.code}).") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise ErrorProveedorFirma("No fue posible descargar el contrato firmado.") from exc
        if len(data) > 20 * 1024 * 1024 or not data.startswith(b"%PDF-"):
            raise ErrorProveedorFirma("El proveedor no devolvió un PDF firmado válido.")
        return data


class ProveedorAprobacionCorreo:
    """Envía un código de un solo uso al adoptante para que lo entregue al albergue."""

    def enviar(self, contrato, callback_url=""):
        from .models import AprobacionContratoCorreo, ContratoAdopcion
        adoptante_correo = (contrato.snapshot.get("adoptanteCorreo") or "").strip()
        if not adoptante_correo:
            raise ErrorProveedorFirma("El adoptante no tiene un correo registrado para recibir el código.")

        codigo = f"{secrets.randbelow(100_000_000):08d}"
        digest = hmac.new(
            settings.SECRET_KEY.encode("utf-8"),
            f"{contrato.public_id}:{codigo}".encode("ascii"),
            hashlib.sha256,
        ).hexdigest()
        expiration = timezone.now() + timedelta(minutes=settings.CONTRACT_APPROVAL_TOKEN_TTL_MINUTES)
        with transaction.atomic():
            locked = ContratoAdopcion.objects.select_for_update().get(pk=contrato.pk)
            AprobacionContratoCorreo.objects.update_or_create(
                contrato=locked,
                defaults={
                    "correo": adoptante_correo,
                    "estado": AprobacionContratoCorreo.Estado.PENDIENTE,
                    "token_sha256": digest,
                    "contenido_sha256": locked.contenido_sha256,
                    "expira_en": expiration,
                    "consumido_en": None,
                    "intentos_fallidos": 0,
                    "aprobado_por_albergue": None,
                    "direccion_ip": None,
                    "agente_usuario": "",
                },
            )
            locked.expira_en = expiration
            locked.save(update_fields=["expira_en", "actualizado_en"])

        minutes = settings.CONTRACT_APPROVAL_TOKEN_TTL_MINUTES
        subject = f"Código para aprobar el contrato de adopción {contrato.snapshot['contratoNumero']}"
        body = (
            f"Hola {contrato.snapshot.get('adoptanteNombre', 'adoptante')},\n\n"
            f"Tu código de aprobación es: {codigo}\n\n"
            f"Vence en {minutes} minutos y solo se puede usar una vez. Comparte el código con el albergue "
            "cuando hayas revisado y aceptado el contrato. El albergue lo ingresará desde su cuenta Petly.\n\n"
            "Este código confirma el acceso a tu correo y registra tu aprobación del contrato en Petly; "
            "no es una firma digital certificada ni verifica tu identidad.\n\n"
            f"Contrato: {contrato.snapshot['contratoNumero']}\n\n{strip_tags(contrato.contenido_html)}"
        )
        try:
            message = EmailMultiAlternatives(
                subject=subject, body=body, from_email=settings.DEFAULT_FROM_EMAIL, to=[adoptante_correo]
            )
            message.attach_alternative(
                f"<p>Hola {escape(contrato.snapshot.get('adoptanteNombre', 'adoptante'))},</p>"
                f"<p>Tu código de aprobación es <strong>{codigo}</strong>. Vence en {minutes} minutos y solo se usa una vez.</p>"
                "<p>Después de revisar y aceptar el contrato, comparte el código con el albergue para que lo registre en Petly.</p>"
                "<p>Este código registra una aprobación por correo; no es una firma digital certificada.</p>"
                f"<hr>{contrato.contenido_html}",
                "text/html",
            )
            message.send(fail_silently=False)
        except Exception as exc:
            raise ErrorProveedorFirma("No se pudo enviar el código al correo del adoptante.") from exc

        return {
            "id": f"email-approval:{contrato.public_id}:{contrato.contenido_sha256[:12]}",
            "url": "",
            "status": "sent",
        }


def obtener_adaptador_firma():
    """Carga una implementación específica sin acoplarla al caso de uso."""
    try:
        clase_adaptador = import_string(settings.CONTRACT_SIGNATURE_ADAPTER_CLASS)
        adaptador = clase_adaptador()
    except (ImportError, AttributeError, TypeError) as exc:
        raise ProveedorNoConfigurado("CONTRACT_SIGNATURE_ADAPTER_CLASS no apunta a un adaptador válido.") from exc
    if not callable(getattr(adaptador, "enviar", None)):
        raise ProveedorNoConfigurado("El adaptador de firma debe implementar enviar(contrato, callback_url).")
    return adaptador
