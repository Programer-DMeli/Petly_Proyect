from datetime import datetime, timedelta, timezone
from io import BytesIO

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
from pyhanko.pdf_utils.incremental_writer import IncrementalPdfFileWriter
from pyhanko.sign import fields, signers
from pyhanko.stamp import TextStampStyle
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen.canvas import Canvas

from .models import CertificadoVeterinario


FIRMA_RECUADRO = (196, A4[1] - 753, 540, A4[1] - 712)


def generar_identidad_demo(contrasena):
    """Entrega la clave PKCS#8 cifrada y el certificado público autofirmado."""
    clave = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    sujeto = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Petly US-59 - Prueba academica")])
    ahora = datetime.now(timezone.utc)
    certificado = (
        x509.CertificateBuilder().subject_name(sujeto).issuer_name(sujeto)
        .public_key(clave.public_key()).serial_number(x509.random_serial_number())
        .not_valid_before(ahora - timedelta(minutes=5)).not_valid_after(ahora + timedelta(days=365))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.KeyUsage(
            digital_signature=True, content_commitment=True, key_encipherment=False, data_encipherment=False,
            key_agreement=False, key_cert_sign=False, crl_sign=False, encipher_only=False, decipher_only=False,
        ), critical=True)
        .sign(clave, hashes.SHA256())
    )
    return (
        clave.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                            serialization.BestAvailableEncryption(contrasena)),
        certificado.public_bytes(serialization.Encoding.PEM),
    )


def pdf_veterinario_demo(tipo):
    etiqueta = CertificadoVeterinario.Tipo(tipo).label
    destino = BytesIO()
    pagina = Canvas(destino, pagesize=A4)
    pagina.setTitle(f"Ficha veterinaria - {etiqueta} - Muestra de prueba")
    pagina.setAuthor("Petly")
    ancho, alto = A4
    izquierda, derecha = 51, ancho - 51
    verde = "#126B55"
    tinta = "#173D33"
    linea = "#CEDDD7"
    fondo = "#F3F8F5"

    def texto(x, y, contenido, *, tamano=9, negrita=False, color=tinta, alinear_derecha=False):
        pagina.setFont("Helvetica-Bold" if negrita else "Helvetica", tamano)
        pagina.setFillColor(HexColor(color))
        dibujar = pagina.drawRightString if alinear_derecha else pagina.drawString
        dibujar(x, alto - y, contenido)

    def trazo(x1, y1, x2, y2, *, color=linea, grosor=0.55):
        pagina.setStrokeColor(HexColor(color))
        pagina.setLineWidth(grosor)
        pagina.line(x1, alto - y1, x2, alto - y2)

    def recuadro(x, y, limite, altura, *, relleno=None, borde=linea):
        pagina.setStrokeColor(HexColor(borde))
        pagina.setFillColor(HexColor(relleno or "#FFFFFF"))
        pagina.setLineWidth(0.55)
        pagina.rect(x, alto - y - altura, limite - x, altura, stroke=1, fill=bool(relleno))

    def seccion(y, titulo):
        recuadro(izquierda, y, derecha, 23, relleno="#E5F2ED", borde=verde)
        texto(izquierda + 9, y + 15, titulo, tamano=10, negrita=True, color=verde)

    def fila(y, valores, separacion, *, encabezado=False, altura=21):
        recuadro(izquierda, y, derecha, altura, relleno=fondo if encabezado else None)
        trazo(separacion, y, separacion, y + altura)
        texto(izquierda + 8, y + 14, valores[0], negrita=encabezado)
        texto(separacion + 8, y + 14, valores[1], negrita=encabezado)

    trazo(izquierda, 46, derecha, 46, color=verde, grosor=3)
    texto(izquierda, 103, "PETLY", tamano=22, negrita=True, color=verde)
    texto(izquierda, 116, "REGISTRO DE BIENESTAR Y SALUD ANIMAL", tamano=8)
    texto(derecha, 95, "FICHA VETERINARIA", tamano=17, negrita=True, alinear_derecha=True)
    texto(derecha, 109, f"Tipo de certificado: {etiqueta}", alinear_derecha=True)
    texto(derecha, 121, "Muestra de prueba · sin validez veterinaria", tamano=8, alinear_derecha=True)
    trazo(izquierda, 134, derecha, 134, color=verde, grosor=1)

    recuadro(izquierda, 145, derecha, 54, relleno=fondo, borde=verde)
    trazo(izquierda, 145, izquierda, 199, color=verde, grosor=2.5)
    texto(izquierda + 10, 162, "DOCUMENTO DE DEMOSTRACIÓN", negrita=True)
    texto(izquierda + 10, 176, "La información consignada es ficticia y se presenta únicamente con fines académicos.")
    texto(izquierda + 10, 189, "Este documento no constituye un certificado veterinario válido.")

    seccion(219, "01. IDENTIFICACIÓN DE LA MASCOTA")
    identificacion = (
        ("Nombre", "Nube (ficticia)"), ("Código", "DEMO-US59"),
        ("Especie", "Gato"), ("Raza", "Mestiza"), ("Sexo", "Hembra"),
    )
    for y, (campo, valor) in enumerate(identificacion):
        base = 257 + 18 * y
        texto(izquierda + 5, base, campo, negrita=True)
        texto(178, base, valor)
        trazo(173, base + 6, derecha, base + 6)

    seccion(350, "02. INDICADORES DE SALUD")
    indicadores = (("Vacunas al día", "Sí"), ("Esterilización", "Sí"), ("Desparasitación", "Sin registrar"))
    fila(378, ("Indicador", "Estado registrado"), ancho / 2, encabezado=True)
    for indice, valores in enumerate(indicadores, start=1):
        fila(378 + 21 * indice, valores, ancho / 2)

    seccion(480, "03. OBSERVACIONES VETERINARIAS")
    observaciones = (
        ("Estado de salud general", "Datos ficticios para la demostración."),
        ("Necesidades especiales", "Sin registrar"),
    )
    for indice, (campo, valor) in enumerate(observaciones):
        y = 508 + 39 * indice
        recuadro(izquierda, y, derecha, 19, relleno=fondo)
        texto(izquierda + 8, y + 13, campo, negrita=True)
        recuadro(izquierda, y + 19, derecha, 20)
        texto(izquierda + 8, y + 33, valor)

    seccion(602, "04. HISTORIAL CLÍNICO")
    fila(630, ("Fecha", "Registro / atención"), 151, encabezado=True)
    fila(651, ("18/11/2026", "Vacunación triple felina (registro ficticio)"), 151)
    fila(672, ("10/11/2026", "Esterilización (registro ficticio)"), 151)

    recuadro(izquierda, 708, derecha, 49, borde="#5F766D")
    recuadro(izquierda, 708, 188, 49, relleno=fondo, borde="#5F766D")
    texto(izquierda + 8, 736, "Validación digital", negrita=True)
    texto(izquierda + 5, 772, "La firma digital de prueba tiene fines académicos y carece de validez veterinaria.", tamano=8)
    trazo(izquierda, 794, derecha, 794)
    texto(izquierda, 811, "Petly | Ficha veterinaria", tamano=7)
    texto(derecha, 811, "Página 1", tamano=7, alinear_derecha=True)
    pagina.save()
    return destino.getvalue()


def firmar_pdf_demo(contenido, firmante):
    destino = BytesIO()
    firmador = signers.PdfSigner(
        signature_meta=signers.PdfSignatureMetadata(
            field_name="FirmaAcademicaPetly", md_algorithm="sha256",
            subfilter=fields.SigSeedSubFilter.PADES, reason="Muestra de prueba",
        ),
        signer=firmante,
        stamp_style=TextStampStyle(
            border_width=0,
            stamp_text="Firmado digitalmente por %(signer)s\nFecha de firma: %(ts)s",
            timestamp_format="%d/%m/%Y %H:%M %z",
        ),
        new_field_spec=fields.SigFieldSpec(sig_field_name="FirmaAcademicaPetly", box=FIRMA_RECUADRO),
    )
    firmador.sign_pdf(
        IncrementalPdfFileWriter(BytesIO(contenido)),
        output=destino,
    )
    return destino.getvalue()
