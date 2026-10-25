"""Reglas versionadas de compatibilidad, alineadas con el motor Spring US-01."""
from decimal import Decimal, ROUND_HALF_UP

from .models import ReglaCompatibilidad


def puntuar_compatibilidad(perfil, mascota, reglas=None):
    """Calcula una puntuación versionada con los pesos configurados globalmente."""
    if reglas is None:
        reglas, _ = ReglaCompatibilidad.objects.get_or_create(pk=1)
    pesos = {
        "vivienda": reglas.peso_vivienda,
        "rutina": reglas.peso_rutina,
        "convivencia": reglas.peso_convivencia,
    }
    criterios = {}
    perfil_finalizado = bool(perfil.cuestionario_completado)

    vivienda = perfil.tipo_vivienda.strip() if perfil.tipo_vivienda else ""
    tamano_conocido = bool(mascota.tamanio)
    if perfil_finalizado and vivienda and mascota.especie and tamano_conocido:
        vivienda_normalizada = vivienda.lower()
        departamento = "depart" in vivienda_normalizada or "apartamento" in vivienda_normalizada
        perro_grande = mascota.especie == "PERRO" and mascota.tamanio == "GRANDE"
        criterios["vivienda"] = 60 if departamento and perro_grande else 100

    energia = {"BAJA": 0, "MEDIA": 1, "ALTA": 2}.get(mascota.energia)
    if perfil_finalizado and perfil.horas_fuera is not None and energia is not None:
        horas = perfil.horas_fuera
        banda_horaria = 2 if horas <= 4 else 1 if horas <= 8 else 0
        distancia = abs(banda_horaria - energia)
        criterios["rutina"] = 100 if distancia == 0 else 70 if distancia == 1 else 35

    temperamento = mascota.temperamento
    afinidad = {
        "SOCIABLE": 100,
        "TRANQUILO": 90,
        "JUGUETON": 80,
        "ACTIVO": 75,
        "INDEPENDIENTE": 70,
        "TIMIDO": 60,
    }.get(temperamento)
    if (perfil_finalizado and perfil.tiene_ninos is not None
            and perfil.tiene_otras_mascotas is not None and afinidad is not None):
        if perfil.tiene_ninos and temperamento == "TIMIDO":
            afinidad = 45
        if perfil.tiene_otras_mascotas and temperamento == "ACTIVO":
            afinidad = 65
        criterios["convivencia"] = afinidad

    cobertura = sum(pesos[key] for key in criterios)
    criterios_explicables = {
        key: {"peso": pesos[key], "puntaje": value, "aporte": str(
            (Decimal(pesos[key]) * Decimal(value) / Decimal(100)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        )}
        for key, value in criterios.items()
    }
    insuficiente = cobertura < 100
    puntaje = None
    if not insuficiente:
        ponderado = sum(
            Decimal(pesos[criterio]) * Decimal(valor) / Decimal(100)
            for criterio, valor in criterios.items()
        )
        puntaje = int(ponderado.quantize(Decimal("1"), rounding=ROUND_HALF_UP))

    mensaje = (
        "Completa tu perfil para calcular compatibilidad."
        if not perfil_finalizado
        else "Información insuficiente para calcular compatibilidad."
        if insuficiente
        else "Puntaje orientativo; el albergue decide cada adopción."
    )
    return {
        "puntaje": puntaje,
        "porcentaje": puntaje,
        "cobertura": cobertura,
        "datosInsuficientes": insuficiente,
        "mensaje": mensaje,
        "versionReglas": f"petly-compat-v{reglas.version_reglas}",
        "criterios": criterios_explicables,
    }
