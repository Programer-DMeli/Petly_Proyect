"""Exportación XLSX/PDF del reporte de adopciones (US-68)."""
from io import BytesIO
from xml.sax.saxutils import escape

from django.http import HttpResponse
from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle
from rest_framework import permissions, serializers
from rest_framework.views import APIView

from .analitica import (
    EsAdministradorPetly,
    FiltroRangoAnaliticaSerializer,
    entregas_adopcion,
    resumir_adopciones,
)


class FiltroExportacionAnaliticaSerializer(FiltroRangoAnaliticaSerializer):
    formato = serializers.ChoiceField(choices=("xlsx", "pdf"), required=True)


MESES = (
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
CONTENT_TYPES = {
    "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "pdf": "application/pdf",
}


def _periodo(rango, clave):
    fecha = rango.get(clave)
    return fecha.strftime("%d/%m/%Y") if fecha else "Sin límite"


def _nombre_archivo(rango, formato):
    desde = rango.get("desde")
    hasta = rango.get("hasta")
    inicio = desde.isoformat() if desde else "inicio"
    fin = hasta.isoformat() if hasta else "actualidad"
    return f"reporte-adopciones-{inicio}-a-{fin}.{formato}"


def _excel_reporte(resumen, rango):
    libro = Workbook()
    resumen_hoja = libro.active
    resumen_hoja.title = "Resumen"
    resumen_hoja.append(["Reporte de adopciones", ""])
    resumen_hoja.append(["Fecha inicial", _periodo(rango, "desde")])
    resumen_hoja.append(["Fecha final", _periodo(rango, "hasta")])
    resumen_hoja.append(["Total de adopciones entregadas", resumen["total_adopciones"]])
    resumen_hoja["A1"].font = Font(bold=True, size=14, color="125643")
    resumen_hoja.column_dimensions["A"].width = 36
    resumen_hoja.column_dimensions["B"].width = 24

    por_mes = libro.create_sheet("Por mes")
    por_mes.append(["Año", "Mes", "Adopciones entregadas"])
    for fila in resumen["por_mes"]:
        por_mes.append([fila["anio"], MESES[fila["mes"] - 1].capitalize(), fila["total"]])
    _estilizar_hoja(por_mes, [14, 18, 27])

    por_especie = libro.create_sheet("Por especie")
    por_especie.append(["Especie", "Adopciones entregadas"])
    for fila in resumen["por_especie"]:
        por_especie.append([fila["nombre"], fila["total"]])
    _estilizar_hoja(por_especie, [24, 27])

    archivo = BytesIO()
    libro.save(archivo)
    return archivo.getvalue()


def _estilizar_hoja(hoja, anchos):
    relleno = PatternFill("solid", fgColor="126B55")
    for celda in hoja[1]:
        celda.font = Font(bold=True, color="FFFFFF")
        celda.fill = relleno
        celda.alignment = Alignment(vertical="center")
    hoja.freeze_panes = "A2"
    hoja.auto_filter.ref = hoja.dimensions
    for columna, ancho in enumerate(anchos, start=1):
        hoja.column_dimensions[chr(64 + columna)].width = ancho


def _tabla_pdf(filas, anchos):
    tabla = Table(filas, colWidths=anchos, repeatRows=1, hAlign="LEFT")
    tabla.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#126b55")),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#c8ded5")),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f2f8f5")]),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    return tabla


def _pdf_reporte(resumen, rango):
    buffer = BytesIO()
    estilos = getSampleStyleSheet()
    estilos.add(ParagraphStyle(
        name="PetlyReportTitle", parent=estilos["Title"], alignment=TA_CENTER,
        textColor=colors.HexColor("#125643"), fontSize=18, leading=22, spaceAfter=8,
    ))
    documento = SimpleDocTemplate(
        buffer, pagesize=landscape(A4), leftMargin=18 * mm, rightMargin=18 * mm,
        topMargin=16 * mm, bottomMargin=16 * mm, title="Reporte de adopciones", author="Petly",
    )
    elementos = [
        Paragraph("Reporte de adopciones", estilos["PetlyReportTitle"]),
        Paragraph(
            f"Periodo de entrega: {_periodo(rango, 'desde')} al {_periodo(rango, 'hasta')}",
            estilos["BodyText"],
        ),
        Spacer(1, 5 * mm),
        Paragraph(f"Total de adopciones entregadas: <b>{resumen['total_adopciones']}</b>", estilos["BodyText"]),
        Spacer(1, 6 * mm),
        Paragraph("Adopciones por mes", estilos["Heading2"]),
    ]
    filas_mes = [["Año", "Mes", "Adopciones entregadas"]]
    filas_mes.extend([
        [str(fila["anio"]), MESES[fila["mes"] - 1].capitalize(), str(fila["total"])]
        for fila in resumen["por_mes"]
    ] or [["-", "Sin datos para el periodo", "0"]])
    elementos.extend([_tabla_pdf(filas_mes, [35 * mm, 70 * mm, 65 * mm]), Spacer(1, 6 * mm)])
    elementos.append(Paragraph("Adopciones por especie", estilos["Heading2"]))
    filas_especie = [["Especie", "Adopciones entregadas"]]
    filas_especie.extend([
        [escape(str(fila["nombre"])), str(fila["total"])]
        for fila in resumen["por_especie"]
    ] or [["Sin datos", "0"]])
    elementos.append(_tabla_pdf(filas_especie, [85 * mm, 85 * mm]))
    documento.build(elementos)
    return buffer.getvalue()


class ExportarAnaliticaAdopcionesView(APIView):
    """Descarga XLSX o PDF con los mismos agregados del panel ADMIN."""

    permission_classes = [permissions.IsAuthenticated, EsAdministradorPetly]

    def get(self, request):
        filtros = FiltroExportacionAnaliticaSerializer(data=request.query_params)
        filtros.is_valid(raise_exception=True)
        opciones = filtros.validated_data
        resumen = resumir_adopciones(
            entregas_adopcion(desde=opciones.get("desde"), hasta=opciones.get("hasta"))
        )
        formato = opciones["formato"]
        contenido = _excel_reporte(resumen, opciones) if formato == "xlsx" else _pdf_reporte(resumen, opciones)
        respuesta = HttpResponse(contenido, content_type=CONTENT_TYPES[formato])
        respuesta["Content-Disposition"] = f'attachment; filename="{_nombre_archivo(opciones, formato)}"'
        respuesta["Cache-Control"] = "private, no-store"
        respuesta["X-Content-Type-Options"] = "nosniff"
        return respuesta
