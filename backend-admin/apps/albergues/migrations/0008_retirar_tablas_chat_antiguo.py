import os
from collections import defaultdict, deque

from django.db import migrations
from django.utils import timezone


HILOS = "albergues_conversacionadopcion"
MENSAJES = "albergues_mensajeconversacion"


def verificar_historial(apps, connection, tablas):
    """Devuelve la correspondencia de IDs de las filas históricas comprobadas."""
    if HILOS not in tablas:
        if MENSAJES in tablas:
            raise RuntimeError("La tabla de mensajes históricos quedó sin su tabla de hilos.")
        return {"hilos": {}, "mensajes": {}}

    alias = connection.alias
    conversaciones = apps.get_model("mensajeria", "Conversacion").objects.using(alias)
    participantes = apps.get_model("mensajeria", "ParticipanteConversacion").objects.using(alias)
    mensajes = apps.get_model("mensajeria", "Mensaje").objects.using(alias)
    mapa = {"hilos": {}, "mensajes": {}}

    def fecha_orm(fecha):
        if connection.timezone is not None and timezone.is_naive(fecha):
            return timezone.make_aware(fecha, connection.timezone)
        return fecha

    with connection.cursor() as cursor:
        cursor.execute(
            f"SELECT h.id, h.adoptante_id, h.albergue_id, h.creada_en, "
            f"h.actualizada_en, a.usuario_id FROM {HILOS} h "
            "LEFT JOIN albergues_albergue a ON a.id = h.albergue_id ORDER BY h.id"
        )
        for hilo, adoptante, albergue, inicio, fin, propietario in cursor.fetchall():
            try:
                actual = conversaciones.get(adoptante_id=adoptante, albergue_id=albergue)
            except (conversaciones.model.DoesNotExist, conversaciones.model.MultipleObjectsReturned):
                raise RuntimeError(f"Hilo histórico {hilo} sin destino único.") from None
            miembros = set(participantes.filter(conversacion_id=actual.pk).values_list("usuario_id", flat=True))
            if not {adoptante, propietario}.issubset(miembros) or actual.creada_en > fecha_orm(inicio) or actual.actualizada_en < fecha_orm(fin):
                raise RuntimeError(f"Hilo histórico {hilo} con participantes o fechas pendientes de conciliar.")
            mapa["hilos"][hilo] = actual.pk
            if MENSAJES not in tablas:
                continue

            copias = defaultdict(deque)
            for pk, remitente, fecha, texto in mensajes.filter(conversacion_id=actual.pk).order_by("pk").values_list(
                "pk", "remitente_id", "creado_en", "texto"
            ).iterator():
                copias[remitente, fecha, texto].append(pk)

            cursor.execute(
                f"SELECT id, remitente_id, creado_en, contenido FROM {MENSAJES} "
                "WHERE conversacion_id = %s ORDER BY id", [hilo],
            )
            for pk, remitente, fecha, texto in cursor.fetchall():
                candidatos = copias[remitente, fecha_orm(fecha), texto]
                if not candidatos:
                    raise RuntimeError(f"Mensaje histórico {pk} sin copia equivalente disponible.")
                mapa["mensajes"][pk] = candidatos.popleft()

        if MENSAJES in tablas:
            cursor.execute(f"SELECT COUNT(*) FROM {MENSAJES}")
            if cursor.fetchone()[0] != len(mapa["mensajes"]):
                raise RuntimeError("Hay mensajes históricos sin un hilo conciliado.")
    return mapa


def retirar_tablas(apps, schema_editor):
    connection = schema_editor.connection
    if connection.settings_dict["NAME"] == "database_petly" and os.environ.get("PETLY_AUTORIZAR_RETIRO_CHAT") != "1":
        raise RuntimeError("El retiro en database_petly requiere autorización explícita de despliegue.")

    tablas = set(connection.introspection.table_names())
    if connection.vendor == "mysql":
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT TABLE_SCHEMA, TABLE_NAME, CONSTRAINT_NAME "
                "FROM information_schema.KEY_COLUMN_USAGE "
                "WHERE REFERENCED_TABLE_SCHEMA = DATABASE() "
                "AND REFERENCED_TABLE_NAME IN (%s, %s) "
                "AND (TABLE_SCHEMA <> DATABASE() OR TABLE_NAME NOT IN (%s, %s)) "
                "LIMIT 1",
                [HILOS, MENSAJES, HILOS, MENSAJES],
            )
            if cursor.fetchone():
                raise RuntimeError("Hay claves foráneas externas hacia las tablas del chat histórico.")
    verificar_historial(apps, connection, tablas)

    for tabla in (MENSAJES, HILOS):
        if tabla in tablas:
            schema_editor.execute(f"DROP TABLE {schema_editor.quote_name(tabla)}")


class Migration(migrations.Migration):
    atomic = False
    dependencies = [
        ("albergues", "0007_metric_respuesta_chatbot"),
    ]
    operations = [migrations.RunPython(retirar_tablas, atomic=False)]
