"""Adaptador REST neutro; se puede sustituir por un proveedor con otro contrato API."""
import json
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

from django.conf import settings
from django.utils.module_loading import import_string


class ErrorProveedorFirma(Exception):
    pass


class ProveedorNoConfigurado(ErrorProveedorFirma):
    pass


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
        email_adoptante = (contrato.snapshot.get("adoptanteCorreo") or "").strip()
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
            "signaturePolicy": "all_signers",
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
        """Fetch the final PDF after the provider reports both signatures complete."""
        endpoint = settings.CONTRACT_SIGNATURE_API_URL.rstrip("/") + "/" + str(contrato.proveedor_documento_id) + "/download"
        parts = urlsplit(endpoint)
        allowed = {"https"} | ({"http"} if settings.DEBUG else set())
        if parts.scheme not in allowed or not parts.netloc:
            raise ProveedorNoConfigurado("La descarga del contrato debe usar HTTPS (HTTP solo en DEBUG).")
        req = Request(endpoint, headers={
            "Authorization": f"Bearer {settings.CONTRACT_SIGNATURE_API_TOKEN}",
            "Accept": "application/pdf",
        }, method="GET")
        try:
            with urlopen(req, timeout=settings.CONTRACT_SIGNATURE_TIMEOUT_SECONDS) as response:
                data = response.read(20 * 1024 * 1024 + 1)
                if len(data) > 20 * 1024 * 1024 or not data.startswith(b"%PDF-"):
                    raise ErrorProveedorFirma("El proveedor no devolvió un PDF firmado válido.")
                return data
        except HTTPError as exc:
            raise ErrorProveedorFirma(f"No se pudo descargar el contrato firmado (HTTP {exc.code}).") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise ErrorProveedorFirma("No fue posible descargar el contrato firmado.") from exc


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
