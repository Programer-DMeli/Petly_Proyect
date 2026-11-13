from io import BytesIO
from xml.sax.saxutils import escape

from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


def _texto_seguro(value):
    return escape(str(value)) if value else "-"


def _nombre_usuario(user, fallback):
    if user is None:
        return fallback
    return user.get_full_name().strip() or user.email or user.username


def generar_certificado_expediente(solicitud):
    """Genera una copia descargable del cierre persistido de una adopción (US-38)."""
    expediente = getattr(solicitud, "expediente_post_adopcion", None)
    if expediente is None or expediente.cerrado_en is None:
        raise ValueError("El expediente todavía no está cerrado.")

    mascota = solicitud.mascota
    albergue = solicitud.albergue
    adoptante = solicitud.adoptante
    nombre_adoptante = _nombre_usuario(adoptante, "Cuenta adoptante no disponible")
    nombre_responsable = _nombre_usuario(expediente.cerrado_por, albergue.nombre)
    cerrado_en = timezone.localtime(expediente.cerrado_en)

    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(
        name="PetlyTitle", parent=styles["Title"], alignment=TA_CENTER,
        textColor=colors.HexColor("#126b55"), fontSize=19, leading=24, spaceAfter=8 * mm,
    ))
    styles.add(ParagraphStyle(
        name="PetlyBody", parent=styles["BodyText"], fontSize=10.5,
        leading=16, textColor=colors.HexColor("#243b35"),
    ))
    styles.add(ParagraphStyle(
        name="PetlyLabel", parent=styles["BodyText"], fontSize=9,
        textColor=colors.HexColor("#52736a"), leading=12,
    ))

    buffer = BytesIO()
    document = SimpleDocTemplate(
        buffer, pagesize=A4, rightMargin=22 * mm, leftMargin=22 * mm,
        topMargin=24 * mm, bottomMargin=22 * mm,
        title=f"Certificado de seguimiento - {mascota.nombre}",
        author="Petly",
    )
    elements = [
        Paragraph("Certificado de seguimiento posadopción", styles["PetlyTitle"]),
        Paragraph(
            "Se certifica que el albergue responsable registró la evaluación final "
            "del seguimiento de esta adopción.", styles["PetlyBody"],
        ),
        Spacer(1, 8 * mm),
    ]
    rows = [
        [Paragraph("Mascota", styles["PetlyLabel"]), Paragraph(_texto_seguro(mascota.nombre), styles["PetlyBody"])],
        [Paragraph("Adoptante", styles["PetlyLabel"]), Paragraph(_texto_seguro(nombre_adoptante), styles["PetlyBody"])],
        [Paragraph("Albergue", styles["PetlyLabel"]), Paragraph(_texto_seguro(albergue.nombre), styles["PetlyBody"])],
        [Paragraph("Expediente", styles["PetlyLabel"]), Paragraph(f"#{solicitud.pk}", styles["PetlyBody"])],
        [Paragraph("Evaluación de adaptación", styles["PetlyLabel"]),
         Paragraph(f"{expediente.calificacion_adaptacion}/5", styles["PetlyBody"])],
        [Paragraph("Cerrado por", styles["PetlyLabel"]), Paragraph(_texto_seguro(nombre_responsable), styles["PetlyBody"])],
        [Paragraph("Fecha de cierre", styles["PetlyLabel"]),
         Paragraph(cerrado_en.strftime("%d/%m/%Y %H:%M (hora de Lima)"), styles["PetlyBody"])],
    ]
    table = Table(rows, colWidths=[52 * mm, 112 * mm], hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#f2f8f5")),
        ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#c8ded5")),
        ("INNERGRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#d8e8e1")),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4 * mm),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4 * mm),
        ("TOPPADDING", (0, 0), (-1, -1), 3 * mm),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3 * mm),
    ]))
    elements.extend([table, Spacer(1, 8 * mm)])

    observaciones = expediente.observaciones_cierre.strip()
    if observaciones:
        elements.extend([
            Paragraph("Observaciones de cierre", styles["Heading2"]),
            Paragraph(_texto_seguro(observaciones).replace("\n", "<br/>"), styles["PetlyBody"]),
            Spacer(1, 8 * mm),
        ])
    elements.append(Paragraph(
        "Documento generado desde la evaluación y el cierre registrados en Petly.",
        styles["PetlyLabel"],
    ))
    document.build(elements)
    buffer.seek(0)
    return buffer
