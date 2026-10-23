"""Reglas versionadas de compatibilidad, alineadas con el motor Spring US-01."""
from decimal import Decimal, ROUND_HALF_UP

PESOS = {"vivienda": 30, "rutina": 35, "convivencia": 35}
VERSION_REGLAS = "petly-compat-v1"


def puntuar_compatibilidad(perfil, mascota):
    """Devuelve un puntaje solo si el perfil y los tres criterios están completos."""
    criterios = {}
    perfil_finalizado = bool(perfil.cuestionario_completado)

    vivienda = perfil.tipo_vivienda.strip() if perfil.tipo_vivienda else ""
    tamano_conocido = bool(mascota.tamanio)
    if perfil_finalizado and vivienda and mascota.especie and tamano_conocido:
        departamento = "depart" in vivienda.lower()
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

    cobertura = sum(PESOS[criterio] for criterio in criterios)
    insuficiente = cobertura < 100
    puntaje = None
    if not insuficiente:
        ponderado = sum(
            Decimal(PESOS[criterio]) * Decimal(valor) / Decimal(100)
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
        "versionReglas": VERSION_REGLAS,
        "criterios": criterios,
    }
