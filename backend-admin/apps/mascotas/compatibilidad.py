"""Reglas transparentes de ordenamiento para US-02 (sin inferir datos faltantes)."""
from decimal import Decimal, ROUND_HALF_UP

PESOS = {"vivienda": 30, "rutina": 35, "convivencia": 35}


def puntuar_compatibilidad(perfil, mascota):
    criterios = {}

    # La necesidad de patio se usa como aproximación explícita; no se infiere raza.
    if perfil.cuestionario_completado and perfil.tipo_vivienda and mascota.especie and mascota.tamanio:
        vivienda_pequena = any(palabra in perfil.tipo_vivienda.lower() for palabra in ("departamento", "apartamento"))
        requiere_exterior = mascota.especie == "PERRO" and mascota.tamanio == "GRANDE"
        criterios["vivienda"] = 60 if vivienda_pequena and requiere_exterior and not perfil.tiene_patio else 100

    energia = {"BAJA": 0, "MEDIA": 1, "ALTA": 2}.get(mascota.energia)
    if perfil.horas_fuera is not None and energia is not None:
        energia_disponible = 2 if perfil.horas_fuera <= 4 else 1 if perfil.horas_fuera <= 8 else 0
        criterios["rutina"] = 100 if energia == energia_disponible else 70 if abs(energia - energia_disponible) == 1 else 35

    afinidad = {"SOCIABLE": 100, "TRANQUILO": 90, "JUGUETON": 80, "ACTIVO": 75,
                "INDEPENDIENTE": 70, "TIMIDO": 60}.get(mascota.temperamento)
    if perfil.cuestionario_completado and afinidad is not None:
        criterios["convivencia"] = afinidad

    cobertura = sum(PESOS[key] for key in criterios)
    if cobertura != 100:
        return {"puntaje": None, "cobertura": cobertura, "datosInsuficientes": True, "criterios": criterios}
    score = sum(Decimal(PESOS[key]) * Decimal(value) for key, value in criterios.items()) / Decimal(100)
    return {"puntaje": int(score.quantize(Decimal("1"), rounding=ROUND_HALF_UP)), "cobertura": cobertura, "datosInsuficientes": False, "criterios": criterios}
