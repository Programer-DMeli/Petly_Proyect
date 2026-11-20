from io import BytesIO
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import LongTable, Paragraph, SimpleDocTemplate, TableStyle


def _pie_de_pagina(canvas, documento):
    canvas.saveState()
    canvas.setFont("Helvetica", 9)
    canvas.setFillColor(colors.HexColor("#455e56"))
    canvas.drawString(documento.leftMargin, 13 * mm, "Petly | Ficha veterinaria")
    canvas.drawRightString(A4[0] - documento.rightMargin, 13 * mm, f"Página {documento.page}")
    canvas.restoreState()


def generar_ficha_veterinaria(mascota):
    """Genera el PDF desde la ficha vigente de la mascota (US-57)."""
    estilos = getSampleStyleSheet()
    verde = colors.HexColor("#126b55")
    titulo = ParagraphStyle("FichaTitulo", parent=estilos["Heading1"], textColor=verde, spaceAfter=12)
    seccion = ParagraphStyle("FichaSeccion", parent=estilos["Heading2"], textColor=verde, spaceBefore=12)
    texto = ParagraphStyle("FichaTexto", parent=estilos["BodyText"], leading=15)

    def campo(etiqueta, valor):
        seguro = "<br/>".join(escape(linea) for linea in str(valor).splitlines())
        return Paragraph(f"<b>{etiqueta}:</b> {seguro or 'Sin registrar'}", texto)

    contenido = [
        Paragraph("Ficha veterinaria", titulo),
        Paragraph("Identificación de la mascota", seccion),
        campo("Nombre", mascota.nombre),
        campo("Código", mascota.codigo),
        campo("Especie", mascota.get_especie_display()),
        campo("Raza", mascota.raza),
        campo("Sexo", mascota.get_sexo_display()),
        Paragraph("Indicadores de salud", seccion),
        campo("Vacunas al día", "Sí" if mascota.vacunas_al_dia else "No"),
        campo("Esterilización", "Sí" if mascota.esterilizado else "No"),
        campo("Desparasitación", "Sí" if mascota.desparasitado else "No"),
        Paragraph("Observaciones veterinarias", seccion),
        campo("Estado de salud general", mascota.estado_salud_general),
        campo("Necesidades especiales", mascota.necesidades_especiales),
    ]

    filas = [
        [registro.get_tipo_display(), registro.fecha.strftime("%d/%m/%Y")]
        for registro in mascota.registros_clinicos.all()
    ]
    contenido.append(Paragraph("Historial clínico", seccion))
    if filas:
        historial = LongTable(
            [["Procedimiento", "Fecha"]] + filas,
            colWidths=[110 * mm, 50 * mm], repeatRows=1, hAlign="LEFT",
        )
        historial.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), verde),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.HexColor("#eef6f2"), colors.white]),
            ("LEFTPADDING", (0, 0), (-1, -1), 10),
            ("TOPPADDING", (0, 0), (-1, -1), 6),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ]))
        contenido.append(historial)
    else:
        contenido.append(Paragraph("No hay registros clínicos con fecha.", texto))

    buffer = BytesIO()
    documento = SimpleDocTemplate(
        buffer, pagesize=A4, leftMargin=22 * mm, rightMargin=22 * mm,
        topMargin=22 * mm, bottomMargin=22 * mm,
        title=f"Ficha veterinaria - {mascota.nombre}", author="Petly",
    )
    documento.build(contenido, onFirstPage=_pie_de_pagina, onLaterPages=_pie_de_pagina)
    buffer.seek(0)
    return buffer
