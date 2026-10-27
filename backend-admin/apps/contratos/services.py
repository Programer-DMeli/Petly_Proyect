"""Casos de uso transaccionales para generar y enviar contratos (US-09)."""
import hashlib
import uuid
from datetime import timedelta

from django.conf import settings
from django.core.exceptions import ValidationError
from django.db import transaction
from django.template.loader import render_to_string
from django.utils import timezone

from apps.acceso.models import SolicitudAdopcion, SolicitudEvento
from .models import AprobacionContratoCorreo, ContratoAdopcion, EventoWebhookFirma
from .providers import ErrorProveedorFirma, obtener_adaptador_firma, obtener_callback_firma


class TerminosContratoNoConfigurados(Exception):
    pass


def _renderizar_contrato(snapshot):
    return render_to_string("contratos/adopcion.html", {
        "contrato_numero": snapshot["contratoNumero"],
        "adoptante_nombre": snapshot["adoptanteNombre"],
        "adoptante_documento": snapshot["adoptanteDocumento"],
        "albergue_nombre": snapshot["albergueNombre"],
        "albergue_responsable": snapshot["albergueResponsable"],
        "mascota_nombre": snapshot["mascotaNombre"],
        "mascota_especie": snapshot["mascotaEspecie"],
        "mascota_codigo": snapshot["mascotaCodigo"],
        "terminos": snapshot.get("terminos", ""),
        "terminos_version": snapshot.get("terminosVersion", ""),
        "terminos_aprobados_por": snapshot.get("terminosAprobadosPor", ""),
        "generado_en": snapshot["generadoEn"],
    })


@transaction.atomic
def crear_borrador(solicitud_id, actor):
    solicitud = SolicitudAdopcion.objects.select_for_update().select_related(
        "postulante", "mascota", "albergue", "albergue__usuario", "adoptante"
    ).get(pk=solicitud_id)
    if solicitud.estado != SolicitudAdopcion.Estado.APROBADA:
        raise ValidationError("Solo se genera contrato para una solicitud aprobada.")

    existente = ContratoAdopcion.objects.filter(solicitud=solicitud).first()
    if existente:
        return existente, False

    adoptante_nombre = (
        solicitud.adoptante.get_full_name().strip()
        if solicitud.adoptante and solicitud.adoptante.get_full_name().strip()
        else solicitud.postulante.nombre_completo.strip()
    )
    albergue = solicitud.albergue
    contrato_numero = f"PETLY-{solicitud.pk}-{uuid.uuid4().hex[:8].upper()}"
    terminos = settings.ADOPTION_CONTRACT_TERMS.strip()
    snapshot = {
        "contratoNumero": contrato_numero,
        "solicitudId": solicitud.pk,
        "adoptanteNombre": adoptante_nombre,
        "adoptanteDocumento": solicitud.postulante.documento_numero,
        "adoptanteCorreo": (solicitud.adoptante.email if solicitud.adoptante else solicitud.postulante.email),
        "albergueNombre": albergue.nombre,
        "albergueResponsable": albergue.responsable_nombre or albergue.nombre,
        "albergueCorreo": albergue.responsable_email or albergue.email or albergue.usuario.email,
        "mascotaNombre": solicitud.mascota.nombre,
        "mascotaEspecie": solicitud.mascota.get_especie_display(),
        "mascotaCodigo": solicitud.mascota.codigo,
        "terminos": terminos,
        "terminosVersion": settings.ADOPTION_CONTRACT_TERMS_VERSION.strip(),
        "terminosAprobadosPor": settings.ADOPTION_CONTRACT_TERMS_APPROVED_BY.strip(),
        "generadoEn": timezone.now().isoformat(),
    }
    contenido = _renderizar_contrato(snapshot)
    contrato = ContratoAdopcion.objects.create(
        solicitud=solicitud,
        contenido_html=contenido,
        contenido_sha256=hashlib.sha256(contenido.encode("utf-8")).hexdigest(),
        snapshot=snapshot,
        creado_por=actor,
    )
    SolicitudEvento.objects.create(
        solicitud=solicitud,
        tipo=SolicitudEvento.Tipo.CONTRATO_GENERADO,
        estado_nuevo=solicitud.estado,
        descripcion=f"Se generó el borrador {contrato_numero}.",
        actor=actor,
    )
    return contrato, True


def enviar_a_firma(contrato_id):
    """Llama al proveedor fuera de la transacción y usa clave idempotente estable."""
    terminos_configurados = settings.ADOPTION_CONTRACT_TERMS.strip()
    terminos_version = settings.ADOPTION_CONTRACT_TERMS_VERSION.strip()
    terminos_aprobador = settings.ADOPTION_CONTRACT_TERMS_APPROVED_BY.strip()
    if not terminos_configurados or not terminos_version or not terminos_aprobador or not settings.ADOPTION_CONTRACT_TERMS_APPROVED:
        raise TerminosContratoNoConfigurados(
            "Configura los términos, su versión y aprobador, y marca su aprobación antes de solicitar firmas."
        )
    callback_url = "" if settings.CONTRACT_SIGNATURE_PROVIDER == "email-approval" else obtener_callback_firma()
    with transaction.atomic():
        contrato = ContratoAdopcion.objects.select_for_update().select_related(
            "solicitud__adoptante", "solicitud__postulante", "solicitud__albergue__usuario"
        ).get(pk=contrato_id)
        if contrato.estado in (ContratoAdopcion.Estado.FIRMADO, ContratoAdopcion.Estado.APROBADO):
            return contrato
        if contrato.estado == ContratoAdopcion.Estado.EN_FIRMA:
            if settings.CONTRACT_SIGNATURE_PROVIDER != "email-approval":
                return contrato
            aprobacion = AprobacionContratoCorreo.objects.select_for_update().filter(contrato=contrato).first()
            codigo_vigente = (
                aprobacion is not None
                and aprobacion.estado == AprobacionContratoCorreo.Estado.PENDIENTE
                and aprobacion.expira_en > timezone.now()
            )
            if codigo_vigente:
                return contrato
            if aprobacion and aprobacion.estado == AprobacionContratoCorreo.Estado.PENDIENTE:
                aprobacion.estado = AprobacionContratoCorreo.Estado.EXPIRADO
                aprobacion.save(update_fields=["estado"])
            contrato.estado = ContratoAdopcion.Estado.EXPIRADO
            contrato.save(update_fields=["estado", "actualizado_en"])
        if contrato.estado == ContratoAdopcion.Estado.EXPIRADO and settings.CONTRACT_SIGNATURE_PROVIDER == "email-approval":
            pass  # El albergue puede reenviar tras vencer; el adaptador genera un código nuevo.
        elif contrato.estado in (ContratoAdopcion.Estado.RECHAZADO, ContratoAdopcion.Estado.EXPIRADO):
            raise ValidationError("El contrato terminó en el proveedor y no admite reenvío.")
        if (
            contrato.snapshot.get("terminos") != terminos_configurados
            or contrato.snapshot.get("terminosVersion") != terminos_version
            or contrato.snapshot.get("terminosAprobadosPor") != terminos_aprobador
        ):
            hash_anterior = contrato.contenido_sha256
            contrato.snapshot["terminos"] = terminos_configurados
            contrato.snapshot["terminosVersion"] = terminos_version
            contrato.snapshot["terminosAprobadosPor"] = terminos_aprobador
            contrato.contenido_html = _renderizar_contrato(contrato.snapshot)
            contrato.contenido_sha256 = hashlib.sha256(contrato.contenido_html.encode("utf-8")).hexdigest()
            contenido_actualizado = hash_anterior != contrato.contenido_sha256
        else:
            contenido_actualizado = False
        contrato.estado = ContratoAdopcion.Estado.PENDIENTE_FIRMA
        contrato.error_integracion = ""
        contrato.save(update_fields=[
            "snapshot", "contenido_html", "contenido_sha256", "estado",
            "error_integracion", "actualizado_en",
        ])
        if contenido_actualizado:
            SolicitudEvento.objects.create(
                solicitud=contrato.solicitud,
                tipo=SolicitudEvento.Tipo.CONTRATO_GENERADO,
                estado_nuevo=contrato.solicitud.estado,
                descripcion=f"Se completó el borrador del contrato {contrato.snapshot['contratoNumero']}.",
                actor=contrato.creado_por,
            )

    try:
        resultado = obtener_adaptador_firma().enviar(contrato, callback_url)
        if not isinstance(resultado, dict) or not resultado.get("id"):
            raise ErrorProveedorFirma("El adaptador devolvió una respuesta sin identificador de documento.")
    except Exception as exc:
        mensaje_seguro = str(exc)[:500] if isinstance(exc, ErrorProveedorFirma) else "Error inesperado del adaptador de firma."
        ContratoAdopcion.objects.filter(pk=contrato_id).exclude(
            estado=ContratoAdopcion.Estado.FIRMADO
        ).update(estado=ContratoAdopcion.Estado.ERROR, error_integracion=mensaje_seguro)
        if isinstance(exc, ErrorProveedorFirma):
            raise
        raise ErrorProveedorFirma("El adaptador de firma produjo un error inesperado.") from exc

    with transaction.atomic():
        contrato = ContratoAdopcion.objects.select_for_update().get(pk=contrato_id)
        contrato.proveedor = settings.CONTRACT_SIGNATURE_PROVIDER
        contrato.proveedor_documento_id = resultado["id"]
        contrato.url_firma = resultado["url"]
        contrato.enviado_en = contrato.enviado_en or timezone.now()
        contrato.error_integracion = ""
        if (
            settings.CONTRACT_SIGNATURE_PROVIDER != "email-approval"
            and contrato.estado != ContratoAdopcion.Estado.FIRMADO
        ) or (
            settings.CONTRACT_SIGNATURE_PROVIDER == "email-approval"
            and contrato.estado == ContratoAdopcion.Estado.PENDIENTE_FIRMA
        ):
            contrato.estado = ContratoAdopcion.Estado.EN_FIRMA
        contrato.save(update_fields=[
            "proveedor", "proveedor_documento_id", "url_firma", "enviado_en",
            "error_integracion", "estado", "actualizado_en",
        ])
        SolicitudEvento.objects.get_or_create(
            solicitud=contrato.solicitud,
            tipo=SolicitudEvento.Tipo.CONTRATO_ENVIADO,
            defaults={
                "estado_nuevo": contrato.solicitud.estado,
                "descripcion": (
                    "Se envió un código de aprobación al correo del adoptante."
                    if settings.CONTRACT_SIGNATURE_PROVIDER == "email-approval"
                    else "Contrato enviado al proveedor de firma."
                ),
                "actor": contrato.creado_por,
            },
        )
        return contrato


@transaction.atomic
def procesar_evento_proveedor(*, provider, event_id, document_id, status, payload_sha256, occurred_at=None):
    contrato = ContratoAdopcion.objects.select_for_update().filter(
        proveedor_documento_id=document_id,
        proveedor=provider,
    ).select_related("solicitud").first()
    if contrato is None:
        raise ContratoAdopcion.DoesNotExist

    webhook, creado = EventoWebhookFirma.objects.get_or_create(
        proveedor=provider,
        evento_id=event_id,
        defaults={
            "contrato": contrato,
            "estado_recibido": status,
            "payload_sha256": payload_sha256,
        },
    )
    if not creado:
        return contrato, False

    nuevo_estado = {
        "sent": ContratoAdopcion.Estado.EN_FIRMA,
        "pending": ContratoAdopcion.Estado.EN_FIRMA,
        "viewed": ContratoAdopcion.Estado.EN_FIRMA,
        "in_progress": ContratoAdopcion.Estado.EN_FIRMA,
        "signed": ContratoAdopcion.Estado.FIRMADO,
        "completed": ContratoAdopcion.Estado.FIRMADO,
        "declined": ContratoAdopcion.Estado.RECHAZADO,
        "rejected": ContratoAdopcion.Estado.RECHAZADO,
        "expired": ContratoAdopcion.Estado.EXPIRADO,
    }[status.lower()]
    anterior = contrato.estado
    estados_terminales = {
        ContratoAdopcion.Estado.FIRMADO,
        ContratoAdopcion.Estado.RECHAZADO,
        ContratoAdopcion.Estado.EXPIRADO,
    }
    if anterior not in estados_terminales:
        contrato.estado = nuevo_estado
        if nuevo_estado == ContratoAdopcion.Estado.FIRMADO:
            contrato.firmado_en = occurred_at or timezone.now()
        contrato.save(update_fields=["estado", "firmado_en", "actualizado_en"])

        tipo_evento = {
            ContratoAdopcion.Estado.FIRMADO: SolicitudEvento.Tipo.CONTRATO_FIRMADO,
            ContratoAdopcion.Estado.RECHAZADO: SolicitudEvento.Tipo.CONTRATO_RECHAZADO,
            ContratoAdopcion.Estado.EXPIRADO: SolicitudEvento.Tipo.CONTRATO_EXPIRADO,
        }.get(nuevo_estado)
        if tipo_evento and anterior != nuevo_estado:
            SolicitudEvento.objects.create(
                solicitud=contrato.solicitud,
                tipo=tipo_evento,
                estado_nuevo=contrato.solicitud.estado,
                descripcion=f"El proveedor informó: {status.lower()}.",
                ocurrido_en=occurred_at or timezone.now(),
            )
    webhook.procesado_en = timezone.now()
    webhook.save(update_fields=["procesado_en"])
    return contrato, True
