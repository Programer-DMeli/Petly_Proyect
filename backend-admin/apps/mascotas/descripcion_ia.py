"""Provider adapter for human-reviewed trait-based description drafts (US-26)."""
import base64
import json
from urllib.parse import urlsplit
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from django.conf import settings


class DescripcionIAError(Exception):
    pass


class ProveedorDescripcionNoConfigurado(DescripcionIAError):
    pass


class ErrorProveedorDescripcion(DescripcionIAError):
    """Fallo de transporte o respuesta inválida del servicio de visión."""


def _rasgos_clave(mascota):
    """Devuelve solo datos registrados que pueden respaldar la descripción."""
    rasgos = {
        "nombre": mascota.nombre,
        "especie": mascota.get_especie_display(),
        "raza": mascota.raza.strip() if mascota.raza else None,
        "tamanio": mascota.get_tamanio_display() if mascota.tamanio else None,
        "edad": mascota.edad_texto if mascota.edad_texto != "Desconocida" else None,
        "temperamento": mascota.get_temperamento_display()
        if mascota.temperamento and mascota.temperamento != "POR_EVALUAR"
        else None,
        "energia": mascota.get_energia_display()
        if mascota.energia and mascota.energia != "POR_EVALUAR"
        else None,
        "observaciones": mascota.descripcion.strip() if mascota.descripcion else None,
    }
    return {clave: valor for clave, valor in rasgos.items() if valor}


def generar_borrador(mascota):
    if settings.GEMINI_API_KEY:
        from .gemini import descripcion
        return validar_borrador(descripcion(mascota, _rasgos_clave(mascota)))
    endpoint = settings.PET_DESCRIPTION_API_URL
    token = settings.PET_DESCRIPTION_API_TOKEN
    if not endpoint or not token:
        raise ProveedorDescripcionNoConfigurado("Configura PET_DESCRIPTION_API_URL y PET_DESCRIPTION_API_TOKEN.")
    endpoint_url = urlsplit(endpoint)
    esquemas_validos = {"https"} | ({"http"} if settings.DEBUG else set())
    if endpoint_url.scheme not in esquemas_validos or not endpoint_url.netloc:
        raise ProveedorDescripcionNoConfigurado("El endpoint de visión debe usar HTTPS (HTTP solo en DEBUG).")
    rasgos = _rasgos_clave(mascota)
    prompt = (
        "Redacta en español una descripción cálida, atractiva y breve para una publicación de adopción. "
        "Usa únicamente los datos registrados que aparecen en el objeto rasgos. No inventes ni deduzcas "
        "salud, vacunas, edad, raza, temperamento, personalidad, conducta u otras características. "
        "Puedes omitir los datos incompletos. No afirmes que está vacunada, esterilizada ni desparasitada. "
        "Devuelve solamente el texto editable, con un máximo de 500 caracteres."
    )
    request_data = {
        "prompt": prompt,
        "traits": rasgos,
        "maxOutputCharacters": 500,
    }
    # Keep optional photo context for providers that support the earlier visual draft flow;
    # it is never required and the prompt forbids treating it as a source for unrecorded facts.
    if mascota.foto_principal:
        photo = mascota.foto_principal
        if photo.size > 5 * 1024 * 1024:
            raise DescripcionIAError("La foto supera el límite de 5 MB.")
        suffix = photo.name.rsplit(".", 1)[-1].lower()
        mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png"}.get(suffix)
        if not mime:
            raise DescripcionIAError("Usa una foto JPEG o PNG.")
        with photo.open("rb") as stream:
            image = stream.read(5 * 1024 * 1024 + 1)
        if len(image) > 5 * 1024 * 1024:
            raise DescripcionIAError("La foto supera el límite de 5 MB.")
        request_data["image"] = {
            "mimeType": mime,
            "base64": base64.b64encode(image).decode("ascii"),
        }
    payload = json.dumps(request_data, ensure_ascii=False).encode("utf-8")
    req = Request(endpoint, data=payload, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json",
    }, method="POST")
    try:
        with urlopen(req, timeout=settings.PET_DESCRIPTION_TIMEOUT_SECONDS) as response:
            raw = response.read(64 * 1024 + 1)
            if len(raw) > 64 * 1024:
                raise ErrorProveedorDescripcion("La respuesta del proveedor es demasiado grande.")
            data = json.loads(raw.decode("utf-8"))
    except HTTPError as exc:
        raise ErrorProveedorDescripcion(f"El proveedor IA respondió HTTP {exc.code}.") from exc
    except (URLError, TimeoutError, OSError) as exc:
        raise ErrorProveedorDescripcion("No fue posible conectar con el proveedor IA.") from exc
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ErrorProveedorDescripcion("El proveedor IA devolvió una respuesta no válida.") from exc
    return validar_borrador(data.get("description") if isinstance(data, dict) else None)


def validar_borrador(text):
    if not isinstance(text, str) or not text.strip() or len(text.strip()) > 500:
        raise ErrorProveedorDescripcion("El proveedor no devolvió una descripción editable válida.")
    draft = text.strip()
    # Refuse common unsupported claims; the shelter can retry with a corrected response.
    prohibited = ("saludable", "vacun", "desparas", "tiene buena salud", "sano", "sana")
    lowered = draft.casefold()
    if any(term in lowered for term in prohibited):
        raise ErrorProveedorDescripcion("La respuesta incluye inferencias que no se permiten. Conservamos la descripción actual.")
    return draft
