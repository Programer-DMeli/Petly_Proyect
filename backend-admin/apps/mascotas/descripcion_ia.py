"""Vision-provider adapter for human-reviewed pet description drafts (US-29)."""
import base64
import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from django.conf import settings


class DescripcionIAError(Exception):
    pass


class ProveedorDescripcionNoConfigurado(DescripcionIAError):
    pass


def generar_borrador(mascota):
    endpoint = settings.PET_DESCRIPTION_API_URL
    token = settings.PET_DESCRIPTION_API_TOKEN
    if not endpoint or not token:
        raise ProveedorDescripcionNoConfigurado("Configura PET_DESCRIPTION_API_URL y PET_DESCRIPTION_API_TOKEN.")
    if not mascota.foto_principal:
        raise DescripcionIAError("La mascota necesita una foto antes de generar un borrador.")
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
    prompt = (
        "Escribe un borrador breve en español sobre rasgos directamente visibles en la foto. "
        "No infieras nombre, salud, edad exacta, raza confirmada, personalidad ni conducta. "
        "Si un rasgo no se distingue, omítelo. Devuelve solo la descripción editable, máximo 500 caracteres."
    )
    payload = json.dumps({
        "prompt": prompt,
        "image": {"mimeType": mime, "base64": base64.b64encode(image).decode("ascii")},
        "maxOutputCharacters": 500,
    }, ensure_ascii=False).encode("utf-8")
    req = Request(endpoint, data=payload, headers={
        "Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json",
    }, method="POST")
    try:
        with urlopen(req, timeout=settings.PET_DESCRIPTION_TIMEOUT_SECONDS) as response:
            raw = response.read(64 * 1024 + 1)
            if len(raw) > 64 * 1024:
                raise DescripcionIAError("La respuesta del proveedor es demasiado grande.")
            data = json.loads(raw.decode("utf-8"))
    except HTTPError as exc:
        raise DescripcionIAError(f"El proveedor IA respondió HTTP {exc.code}.") from exc
    except (URLError, TimeoutError, OSError) as exc:
        raise DescripcionIAError("No fue posible conectar con el proveedor IA.") from exc
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DescripcionIAError("El proveedor IA devolvió una respuesta no válida.") from exc
    text = data.get("description") if isinstance(data, dict) else None
    if not isinstance(text, str) or not text.strip() or len(text.strip()) > 500:
        raise DescripcionIAError("El proveedor no devolvió una descripción editable válida.")
    draft = text.strip()
    # Refuse common unsupported claims; the shelter can retry with a corrected response.
    prohibited = ("raza", "saludable", "vacun", "desparas", "tiene buena salud", "años", " meses", "temperamento", "personalidad", "se comporta", "agresiv", "obediente")
    lowered = draft.casefold()
    if any(term in lowered for term in prohibited):
        raise DescripcionIAError("La respuesta incluye inferencias que no se permiten. Conservamos la descripción actual.")
    return draft
