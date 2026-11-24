"""Operaciones internas de portabilidad y baja para los datos administrados por Django."""

import base64
import hashlib
import hmac
import secrets
import uuid
from datetime import date, datetime
from decimal import Decimal

from django.conf import settings
from django.contrib.auth import get_user_model
from django.db import transaction
from django.utils import timezone
from rest_framework import permissions, serializers, status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.contratos.models import (
    CasoExitoAdopcion,
    ContratoAdopcion,
    ExpedientePostAdopcion,
    FotoCasoExito,
    FotoReporteBienestar,
    ReporteBienestarPostAdopcion,
    SeguimientoPostAdopcion,
    SolicitudAsesoriaVeterinaria,
)
from apps.mensajeria.models import AdjuntoMensaje, Mensaje, ReporteConversacion
from apps.mensajeria.models import ParticipanteConversacion
from .kyc_storage import PrivateKycStorage
from .models import (
    AdoptanteProfile,
    Autorizacion,
    CanalAvisos,
    CriteriosAviso,
    DispositivoPush,
    EventoAuditoria,
    FotoHogar,
    Postulante,
    SolicitudAdopcion,
    SolicitudEvento,
    SolicitudPrivacidadDjango,
    VerificacionKyc,
)


class SolicitudPrivacidadSerializer(serializers.Serializer):
    usuario_spring_id = serializers.IntegerField(min_value=1)


class BajaPrivacidadSerializer(SolicitudPrivacidadSerializer):
    operacion_id = serializers.UUIDField()
    confirmar = serializers.BooleanField()

    def validate_confirmar(self, value):
        if value is not True:
            raise serializers.ValidationError("La baja requiere confirmación explícita.")
        return value


def _autorizar_servicio(request):
    esperado = settings.INTERNAL_API_SECRET
    recibido = request.headers.get("X-Internal-Secret", "")
    if not esperado:
        return Response(
            {"detail": "La integración interna de privacidad no está configurada."},
            status=status.HTTP_503_SERVICE_UNAVAILABLE,
        )
    if not recibido or not secrets.compare_digest(recibido, esperado):
        return Response({"detail": "Credenciales de servicio inválidas."}, status=status.HTTP_401_UNAUTHORIZED)
    return None


def _usuario_hash(usuario_spring_id):
    secreto = settings.INTERNAL_API_SECRET.encode("utf-8")
    return hmac.new(secreto, str(usuario_spring_id).encode("ascii"), hashlib.sha256).hexdigest()


def _iso(value):
    if isinstance(value, (date, datetime)):
        return value.isoformat()
    if isinstance(value, Decimal):
        return str(value)
    return value


def _registro(instance, campos):
    return {campo: _iso(getattr(instance, campo)) for campo in campos}


def _archivo_exportado(field_file, categoria):
    if not field_file or not field_file.name:
        return None
    with field_file.storage.open(field_file.name, "rb") as contenido:
        datos = base64.b64encode(contenido.read()).decode("ascii")
    return {
        "categoria": categoria,
        "nombre": field_file.name.rsplit("/", 1)[-1],
        "contenido_base64": datos,
    }


def _exportar_datos(usuario):
    """Construye una lista explícita de datos; nunca serializa credenciales Django."""
    perfil = AdoptanteProfile.objects.filter(user=usuario).first()
    kyc = VerificacionKyc.objects.filter(adoptante=usuario).first()
    solicitudes = list(
        SolicitudAdopcion.objects.filter(adoptante=usuario)
        .select_related("postulante", "mascota", "albergue")
        .order_by("fecha_solicitud", "id")
    )
    solicitud_ids = [solicitud.pk for solicitud in solicitudes]
    seguimientos = list(
        SeguimientoPostAdopcion.objects.filter(solicitud_id__in=solicitud_ids)
        .select_related("solicitud")
        .order_by("programado_para", "id")
    )
    seguimiento_ids = [item.pk for item in seguimientos]
    conversaciones = list(
        usuario.conversaciones_adoptante.select_related("albergue").prefetch_related("mensajes__remitente")
    )

    aplicaciones = []
    archivos = []
    for solicitud in solicitudes:
        item = _registro(solicitud, (
            "id", "estado", "mensaje_postulante", "respuestas_cuestionario", "fecha_solicitud",
            "fecha_revision", "fecha_entrega", "estado_revision_veterinaria", "ficha_veterinaria_revisada",
            "fecha_revision_veterinaria", "fecha_recojo_programado", "mensaje_recojo",
            "entrega_satisfaccion", "entrega_observaciones", "entrega_confirmada_en",
        ))
        postulante = solicitud.postulante
        item["postulante"] = _registro(postulante, (
            "documento_tipo", "documento_numero", "nombres", "apellidos", "email", "telefono",
            "fecha_nacimiento", "direccion", "distrito", "provincia", "departamento", "tipo_vivienda",
            "tiene_patio", "tiene_otras_mascotas", "detalles_otras_mascotas", "experiencia_previa",
            "motivo_adopcion", "preferencia_especie", "preferencia_tamanio", "preferencia_edad",
        )) if postulante else None
        item["mascota"] = {"id": solicitud.mascota_id, "nombre": solicitud.mascota.nombre}
        item["albergue"] = {"id": solicitud.albergue_id, "nombre": solicitud.albergue.nombre}
        item["eventos"] = [
            _registro(evento, ("tipo", "estado_anterior", "estado_nuevo", "descripcion", "ocurrido_en"))
            for evento in SolicitudEvento.objects.filter(solicitud=solicitud).order_by("ocurrido_en", "id")
        ]
        contrato = ContratoAdopcion.objects.filter(solicitud=solicitud).first()
        item["contrato"] = _registro(contrato, (
            "public_id", "estado", "proveedor", "contenido_html", "contenido_sha256", "snapshot",
            "generado_en", "enviado_en", "firmado_en", "expira_en",
        )) if contrato else None
        if contrato and contrato.documento_firmado:
            archivo = _archivo_exportado(contrato.documento_firmado, "contrato_firmado")
            if archivo:
                archivos.append(archivo)
                item["contrato"]["documento_firmado"] = archivo["nombre"]
        item["fotos_hogar"] = []
        for foto in FotoHogar.objects.filter(solicitud=solicitud):
            archivo = _archivo_exportado(foto.archivo, "foto_hogar")
            if archivo:
                archivos.append(archivo)
                item["fotos_hogar"].append({"sha256": foto.sha256, "tipo": foto.tipo, "archivo": archivo["nombre"]})
        aplicaciones.append(item)

    reportes = []
    for reporte in ReporteBienestarPostAdopcion.objects.filter(seguimiento_id__in=seguimiento_ids):
        item = _registro(reporte, ("estado_bienestar", "observaciones", "enviado_en"))
        item["seguimiento_id"] = reporte.seguimiento_id
        item["fotos"] = []
        for foto in reporte.fotos.all():
            archivo = _archivo_exportado(foto.archivo, "foto_reporte_bienestar")
            if archivo:
                archivos.append(archivo)
                item["fotos"].append({"mime_type": foto.mime_type, "tamaño_bytes": foto.tamaño_bytes, "archivo": archivo["nombre"]})
        reportes.append(item)

    casos = []
    for caso in CasoExitoAdopcion.objects.filter(autor=usuario):
        item = _registro(caso, ("historia", "estado", "destacado", "motivo_revision", "publicado_en", "creado_en"))
        item["fotos"] = []
        for foto in caso.fotos.all():
            archivo = _archivo_exportado(foto.archivo, "foto_caso_exito")
            if archivo:
                archivos.append(archivo)
                item["fotos"].append({"mime_type": foto.mime_type, "tamaño_bytes": foto.tamaño_bytes, "archivo": archivo["nombre"]})
        casos.append(item)

    chats = []
    for conversacion in conversaciones:
        mensajes = []
        for mensaje in conversacion.mensajes.all():
            mensaje_data = _registro(mensaje, ("texto", "bienvenida_automatica", "creado_en"))
            mensaje_data["remitente"] = "adoptante" if mensaje.remitente_id == usuario.pk else "albergue"
            mensaje_data["adjuntos"] = []
            for adjunto in mensaje.adjuntos.all():
                archivo = _archivo_exportado(adjunto.archivo, "adjunto_mensaje")
                if archivo:
                    archivos.append(archivo)
                    mensaje_data["adjuntos"].append({
                        "nombre_original": adjunto.nombre_original,
                        "content_type": adjunto.content_type,
                        "tamaño_bytes": adjunto.tamaño_bytes,
                        "archivo": archivo["nombre"],
                    })
            mensajes.append(mensaje_data)
        chats.append({
            "creada_en": _iso(conversacion.creada_en),
            "albergue": conversacion.albergue.nombre,
            "mensajes": mensajes,
        })

    user_fields = ("username", "first_name", "last_name", "email", "telefono", "fecha_nacimiento",
                   "date_joined", "acepto_terminos", "fecha_aceptacion_terminos")
    exportacion = {
        "formato": "petly-portabilidad-v1",
        "generado_en": timezone.now().isoformat(),
        "usuario": _registro(usuario, user_fields),
        "perfil": _registro(perfil, (
            "documento_tipo", "documento_numero", "nombres", "apellidos", "direccion", "distrito",
            "provincia", "departamento", "tipo_vivienda", "tiene_patio", "tiene_ninos",
            "tiene_otras_mascotas", "horas_fuera", "detalles_otras_mascotas", "experiencia_previa",
            "motivo_adopcion", "preferencia_especie", "preferencia_tamanio", "preferencia_edad",
            "cuestionario_completado", "fecha_cuestionario",
        )) if perfil else None,
        "verificacion_identidad": _registro(kyc, (
            "dni", "nombres", "apellidos", "estado", "coincide", "proveedor", "fecha_consulta", "creado_en",
        )) if kyc else None,
        "autorizaciones": [
            {**_registro(a, ("estado", "campos_permitidos", "fecha_autorizacion", "fecha_revocacion", "fecha_expiracion")),
             "albergue": a.albergue.nombre}
            for a in Autorizacion.objects.filter(adoptante=usuario).select_related("albergue")
        ],
        "solicitudes_adopcion": aplicaciones,
        "seguimientos": [
            _registro(s, ("programado_para", "modalidad", "requiere_reporte", "estado", "notas", "creado_en"))
            for s in seguimientos
        ],
        "reportes_bienestar": reportes,
        "asesorias_veterinarias": [
            {**_registro(a, ("problema", "estado", "creado_en")), "seguimiento_id": a.seguimiento_id}
            for a in SolicitudAsesoriaVeterinaria.objects.filter(seguimiento_id__in=seguimiento_ids)
        ],
        "expedientes": [
            {**_registro(e, ("calificacion_adaptacion", "observaciones_cierre", "cerrado_en")),
             "solicitud_id": e.solicitud_id}
            for e in ExpedientePostAdopcion.objects.filter(solicitud_id__in=solicitud_ids)
        ],
        "historias_adopcion": casos,
        "conversaciones": chats,
        "reportes_de_conversacion": [
            {**_registro(r, ("motivo", "detalle", "creado_en")), "conversacion_id": r.conversacion_id}
            for r in ReporteConversacion.objects.filter(reportante=usuario)
        ],
        "auditoria": [
            _registro(evento, ("evento_id", "actor_rol", "accion", "origen", "recurso_tipo", "recurso_id", "ocurrido_en"))
            for evento in EventoAuditoria.objects.filter(actor_usuario_id=usuario.usuario_spring_id).order_by("ocurrido_en")
        ],
        "avisos": [
            _registro(evento, ("tipo", "estado_anterior", "estado_nuevo", "descripcion", "ocurrido_en", "leido_en"))
            for evento in SolicitudEvento.objects.filter(destinatario=usuario).order_by("ocurrido_en", "id")
        ],
        "criterios_de_aviso": _registro(CriteriosAviso.objects.filter(adoptante=usuario).first(),
                                         ("activo", "especie", "tamanio", "temperamento", "edad_min_meses", "edad_max_meses"))
        if CriteriosAviso.objects.filter(adoptante=usuario).exists() else None,
        "archivos_privados": archivos,
        "retenciones": [
            "Los contratos firmados y los eventos de auditoría se conservan para mantener evidencia e integridad del historial."
        ],
    }
    return exportacion


def _agregar_archivo(pendientes, field_file, storage):
    if field_file and field_file.name:
        pendientes.append({"storage": storage, "name": field_file.name})


def _procesar_archivos(operacion):
    from apps.contratos.storage import PrivateContractStorage
    from apps.mensajeria.models import PrivateMessageStorage

    storages = {
        "kyc": PrivateKycStorage(),
        "contratos": PrivateContractStorage(),
        "mensajeria": PrivateMessageStorage(),
    }
    pendientes = []
    for archivo in operacion.archivos_pendientes:
        almacenamiento = storages.get(archivo.get("storage"))
        if almacenamiento is None:
            pendientes.append(archivo)
            continue
        try:
            almacenamiento.delete(archivo["name"])
        except OSError:
            pendientes.append(archivo)

    operacion.archivos_pendientes = pendientes
    if pendientes:
        operacion.estado = SolicitudPrivacidadDjango.Estado.ARCHIVOS_PENDIENTES
        operacion.completado_en = None
    else:
        operacion.estado = SolicitudPrivacidadDjango.Estado.COMPLETADA
        operacion.completado_en = timezone.now()
    operacion.save(update_fields=["archivos_pendientes", "estado", "completado_en"])
    return operacion


def _anonimizar_postulante(postulante, usuario):
    if not postulante:
        return
    solicitudes_ajenas = postulante.solicitudes.exclude(adoptante=usuario)
    if solicitudes_ajenas.exists():
        return
    postulante.documento_tipo = "ELIMINADO"
    postulante.documento_numero = f"E{uuid.uuid4().hex[:19]}"
    postulante.nombres = "Datos eliminados"
    postulante.apellidos = ""
    postulante.email = ""
    postulante.telefono = ""
    postulante.fecha_nacimiento = None
    postulante.direccion = postulante.distrito = postulante.provincia = postulante.departamento = ""
    postulante.tipo_vivienda = ""
    postulante.tiene_patio = postulante.tiene_otras_mascotas = False
    postulante.detalles_otras_mascotas = postulante.experiencia_previa = ""
    postulante.motivo_adopcion = postulante.preferencia_especie = ""
    postulante.preferencia_tamanio = postulante.preferencia_edad = ""
    postulante.observaciones_internas = ""
    postulante.estado = Postulante.EstadoPostulante.INACTIVO
    postulante.creado_por = None
    postulante.save()


def _procesar_baja(usuario, operacion_id):
    User = get_user_model()
    with transaction.atomic():
        usuario = User.objects.select_for_update().get(pk=usuario.pk)
        operacion, creada = SolicitudPrivacidadDjango.objects.select_for_update().get_or_create(
            usuario_hash=_usuario_hash(usuario.usuario_spring_id),
            defaults={"operacion_id": operacion_id},
        )
        if not creada and operacion.operacion_id != operacion_id:
            return None, "La cuenta ya tiene otra operación de baja registrada."

        if creada:
            pendientes = []
            solicitudes = list(SolicitudAdopcion.objects.select_for_update().filter(adoptante=usuario))
            solicitud_ids = [s.pk for s in solicitudes]
            postulantes = {s.postulante_id: s.postulante for s in solicitudes if s.postulante_id}
            seguimientos = SeguimientoPostAdopcion.objects.filter(solicitud_id__in=solicitud_ids)
            seguimiento_ids = list(seguimientos.values_list("id", flat=True))
            contrato_ids = list(ContratoAdopcion.objects.filter(solicitud_id__in=solicitud_ids).values_list("id", flat=True))
            retenciones = []
            if contrato_ids:
                retenciones.append({
                    "tipo": "contratos_adopcion",
                    "cantidad": len(contrato_ids),
                    "motivo": "Se conservan para mantener evidencia e integridad contractual; la política legal define el plazo de retención.",
                })
            eventos_auditoria = EventoAuditoria.objects.filter(actor_usuario_id=usuario.usuario_spring_id).count()
            if eventos_auditoria:
                retenciones.append({
                    "tipo": "eventos_auditoria",
                    "cantidad": eventos_auditoria,
                    "motivo": "Registro de seguridad inmutable; se conserva el identificador de actor como evidencia de auditoría.",
                })
            casos_del_usuario = CasoExitoAdopcion.objects.filter(autor=usuario)

            for kyc in VerificacionKyc.objects.filter(adoptante=usuario):
                _agregar_archivo(pendientes, kyc.documento_anverso, "kyc")
                _agregar_archivo(pendientes, kyc.documento_reverso, "kyc")
            for foto in FotoHogar.objects.filter(solicitud_id__in=solicitud_ids):
                _agregar_archivo(pendientes, foto.archivo, "kyc")
            for foto in FotoReporteBienestar.objects.filter(reporte__seguimiento_id__in=seguimiento_ids):
                _agregar_archivo(pendientes, foto.archivo, "contratos")
            for foto in FotoCasoExito.objects.filter(caso__in=casos_del_usuario):
                _agregar_archivo(pendientes, foto.archivo, "contratos")
            for adjunto in AdjuntoMensaje.objects.filter(mensaje__remitente=usuario):
                _agregar_archivo(pendientes, adjunto.archivo, "mensajeria")

            for postulante in postulantes.values():
                _anonimizar_postulante(postulante, usuario)

            # Mantiene solicitudes, mascota, albergue y fechas; quita contenido y datos del solicitante.
            SolicitudAdopcion.objects.filter(pk__in=solicitud_ids).update(
                adoptante=None, mensaje_postulante="", respuestas_cuestionario={},
                observaciones_revision="", ficha_veterinaria_revisada={},
                observaciones_revision_veterinaria="", mensaje_recojo="", entrega_observaciones="",
                revisado_por=None, revision_veterinaria_por=None, recojo_programado_por=None,
                entrega_confirmada_por=None,
            )
            SolicitudEvento.objects.filter(solicitud_id__in=solicitud_ids).update(
                descripcion="", actor=None, destinatario=None,
            )
            ExpedientePostAdopcion.objects.filter(solicitud_id__in=solicitud_ids).update(
                observaciones_cierre="", cerrado_por=None,
            )
            seguimientos.update(notas="", creado_por=None)
            ReporteBienestarPostAdopcion.objects.filter(seguimiento_id__in=seguimiento_ids).update(observaciones="")
            SolicitudAsesoriaVeterinaria.objects.filter(seguimiento_id__in=seguimiento_ids).update(
                problema="", estado=SolicitudAsesoriaVeterinaria.Estado.ATENDIDA,
            )
            casos_del_usuario.update(
                historia="", destacado=False, estado=CasoExitoAdopcion.Estado.RECHAZADO,
                motivo_revision="", revisado_por=None,
            )

            Mensaje.objects.filter(remitente=usuario).update(texto="[Mensaje retirado por baja de cuenta]")
            ReporteConversacion.objects.filter(reportante=usuario).update(detalle="")
            # Quita cualquier referencia de usuario que no tenga valor histórico propio.
            SolicitudEvento.objects.filter(actor=usuario).update(actor=None)
            VerificacionKyc.objects.filter(revisado_por=usuario).update(revisado_por=None)
            Postulante.objects.filter(creado_por=usuario).update(creado_por=None)
            ExpedientePostAdopcion.objects.filter(cerrado_por=usuario).update(cerrado_por=None)
            CasoExitoAdopcion.objects.filter(revisado_por=usuario).update(revisado_por=None)
            ParticipanteConversacion.objects.filter(usuario=usuario).delete()
            SolicitudEvento.objects.filter(destinatario=usuario).delete()

            VerificacionKyc.objects.filter(adoptante=usuario).delete()
            AdoptanteProfile.objects.filter(user=usuario).delete()
            Autorizacion.objects.filter(adoptante=usuario).delete()
            CriteriosAviso.objects.filter(adoptante=usuario).delete()
            CanalAvisos.objects.filter(adoptante=usuario).delete()
            DispositivoPush.objects.filter(adoptante=usuario).delete()
            FotoHogar.objects.filter(solicitud_id__in=solicitud_ids).delete()
            FotoReporteBienestar.objects.filter(reporte__seguimiento_id__in=seguimiento_ids).delete()
            FotoCasoExito.objects.filter(caso__in=casos_del_usuario).delete()
            AdjuntoMensaje.objects.filter(mensaje__remitente=usuario).delete()

            # Las claves foráneas de conversaciones, reportes y casos se conservan con un usuario tombstone.
            usuario.username = f"eliminado_{uuid.uuid4().hex}"
            usuario.first_name = ""
            usuario.last_name = ""
            usuario.email = ""
            usuario.telefono = ""
            usuario.fecha_nacimiento = None
            usuario.acepto_terminos = False
            usuario.fecha_aceptacion_terminos = None
            usuario.last_login = None
            usuario.usuario_spring_id = None
            usuario.is_active = False
            usuario.intentos_fallidos = 0
            usuario.bloqueado_hasta = None
            usuario.set_unusable_password()
            usuario.save(update_fields=(
                "username", "first_name", "last_name", "email", "telefono", "fecha_nacimiento",
                "acepto_terminos", "fecha_aceptacion_terminos", "last_login", "usuario_spring_id", "is_active",
                "intentos_fallidos", "bloqueado_hasta", "password",
            ))
            operacion.archivos_pendientes = pendientes
            operacion.retenciones = retenciones
            operacion.estado = SolicitudPrivacidadDjango.Estado.ARCHIVOS_PENDIENTES
            operacion.save(update_fields=["archivos_pendientes", "retenciones", "estado"])

    return _procesar_archivos(operacion), None


class ExportarDatosAdoptanteInternoView(APIView):
    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        no_autorizado = _autorizar_servicio(request)
        if no_autorizado:
            return no_autorizado
        serializer = SolicitudPrivacidadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        User = get_user_model()
        usuario = User.objects.filter(usuario_spring_id=serializer.validated_data["usuario_spring_id"]).first()
        if not usuario or usuario.rol != User.Rol.ADOPTANTE:
            return Response({"detail": "No se encontró una cuenta adoptante activa."}, status=status.HTTP_404_NOT_FOUND)
        return Response(_exportar_datos(usuario), status=status.HTTP_200_OK)


class BajaDatosAdoptanteInternoView(APIView):
    authentication_classes = []
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        no_autorizado = _autorizar_servicio(request)
        if no_autorizado:
            return no_autorizado
        serializer = BajaPrivacidadSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        datos = serializer.validated_data
        User = get_user_model()
        existente = SolicitudPrivacidadDjango.objects.filter(operacion_id=datos["operacion_id"]).first()
        if existente:
            if not hmac.compare_digest(existente.usuario_hash, _usuario_hash(datos["usuario_spring_id"])):
                return Response({"detail": "El identificador de operación ya está asociado."}, status=status.HTTP_409_CONFLICT)
            existente = _procesar_archivos(existente)
            return Response({
                "operacion_id": existente.operacion_id,
                "estado": existente.estado,
                "archivos_pendientes": len(existente.archivos_pendientes),
                "retenciones": existente.retenciones,
            }, status=status.HTTP_200_OK if existente.estado == SolicitudPrivacidadDjango.Estado.COMPLETADA else status.HTTP_202_ACCEPTED)

        usuario = User.objects.filter(usuario_spring_id=datos["usuario_spring_id"]).first()
        if not usuario or usuario.rol != User.Rol.ADOPTANTE:
            return Response({"detail": "No se encontró una cuenta adoptante activa."}, status=status.HTTP_404_NOT_FOUND)
        operacion, error = _procesar_baja(usuario, datos["operacion_id"])
        if error:
            return Response({"detail": error}, status=status.HTTP_409_CONFLICT)
        return Response({
            "operacion_id": operacion.operacion_id,
            "estado": operacion.estado,
            "archivos_pendientes": len(operacion.archivos_pendientes),
            "retenciones": operacion.retenciones,
        }, status=status.HTTP_200_OK if operacion.estado == SolicitudPrivacidadDjango.Estado.COMPLETADA else status.HTTP_202_ACCEPTED)
