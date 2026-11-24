import { useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage, fetchDjangoFile } from '../../../../services/http';

const tipos = {
  VACUNACION: 'Vacunación',
  ESTERILIZACION: 'Esterilización',
} as const;
type Tipo = keyof typeof tipos;
type Certificado = { tipo: Tipo; validacion: 'PENDIENTE' | 'VERIFICADO' };
type DatosCertificados = { carga_habilitada: boolean; mensaje: string; certificados: Certificado[] };

function VisorCertificado({ ruta, titulo }: { ruta: string; titulo: string }) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    let urlTemporal = '';
    void fetchDjangoFile(ruta, { signal: controller.signal, cache: 'no-store' })
      .then(response => response.blob())
      .then(blob => {
        if (controller.signal.aborted) return;
        if (blob.type !== 'application/pdf') throw new Error('El archivo recibido no es un PDF.');
        urlTemporal = URL.createObjectURL(blob);
        setUrl(urlTemporal);
      })
      .catch(reason => { if (!controller.signal.aborted) setError(errorMessage(reason)); });
    return () => {
      controller.abort();
      if (urlTemporal) URL.revokeObjectURL(urlTemporal);
    };
  }, [ruta]);

  if (error) return <Feedback error={error} />;
  if (!url) return <p role="status">Abriendo certificado…</p>;
  return <>
    <iframe src={url} title={titulo} width="100%" height={420} />
    <a href={url} download={`${titulo}.pdf`}>Descargar PDF</a>
  </>;
}

function CertificadoPorTipo({ tipo, base, habilitado, inicial }: {
  tipo: Tipo; base: string; habilitado: boolean; inicial?: Certificado;
}) {
  const [certificado, setCertificado] = useState(inicial);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [entrada, setEntrada] = useState(0);
  const [visor, setVisor] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');
  const ruta = `${base}${tipo}/`;
  const titulo = `Certificado de ${tipos[tipo].toLowerCase()}`;

  async function guardar() {
    if (!habilitado || !archivo) return;
    setGuardando(true);
    setError('');
    setExito('');
    try {
      const form = new FormData();
      form.set('archivo', archivo);
      setCertificado(await django<Certificado>(ruta, { method: 'PUT', body: form }));
      setArchivo(null);
      setEntrada(actual => actual + 1);
      setVisor(false);
      setExito('Certificado guardado.');
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setGuardando(false);
    }
  }

  return <section className="pet-vet-evidence" aria-label={titulo}>
    <strong>{tipos[tipo]}</strong>
    <p className="muted">{certificado ? (
      certificado.validacion === 'VERIFICADO' ? 'Firma verificada.' : 'Firma pendiente de verificación.'
    ) : 'Sin certificado guardado.'}</p>
    <label>
      {titulo} (PDF)
      <input key={entrada} type="file" accept="application/pdf,.pdf" disabled={!habilitado || guardando}
        onChange={event => setArchivo(event.target.files?.[0] ?? null)} />
    </label>
    <div className="actions">
      <button type="button" className="secondary" disabled={!habilitado || !archivo || guardando}
        onClick={() => void guardar()}>
        {guardando ? 'Verificando firma…' : certificado ? 'Reemplazar certificado' : 'Adjuntar certificado'}
      </button>
      {certificado?.validacion === 'VERIFICADO' && (
        <button type="button" className="secondary" onClick={() => setVisor(actual => !actual)}>
          {visor ? 'Cerrar visor' : 'Ver certificado'}
        </button>
      )}
    </div>
    <Feedback error={error} success={exito} />
    {visor && <VisorCertificado ruta={ruta} titulo={titulo} />}
  </section>;
}

export default function CertificadosVeterinarios({ mascotaId }: { mascotaId: number | null }) {
  if (!mascotaId) return <section aria-label="Certificados veterinarios">
    <h5>Certificados veterinarios</h5>
    <p>Guarda la mascota para consultar sus certificados.</p>
  </section>;
  return <CertificadosDeMascota key={mascotaId} mascotaId={mascotaId} />;
}

function CertificadosDeMascota({ mascotaId }: { mascotaId: number }) {
  const [datos, setDatos] = useState<DatosCertificados | null>(null);
  const [error, setError] = useState('');
  const base = `/api/mascotas/${mascotaId}/certificados-veterinarios/`;

  useEffect(() => {
    const controller = new AbortController();
    void django<DatosCertificados>(base, { signal: controller.signal, cache: 'no-store' })
      .then(respuesta => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(respuesta.certificados)) throw new Error('No se pudieron consultar los certificados.');
        setDatos(respuesta);
      })
      .catch(reason => { if (!controller.signal.aborted) setError(errorMessage(reason)); });
    return () => controller.abort();
  }, [base]);

  return <section aria-label="Certificados veterinarios">
    <h5>Certificados veterinarios</h5>
    {!error && !datos && <p role="status">Consultando certificados…</p>}
    <Feedback error={error} />
    {datos && <>
      {datos.mensaje && <p className="muted">{datos.mensaje}</p>}
      <div className="grid">
        {(Object.keys(tipos) as Tipo[]).map(tipo => (
          <CertificadoPorTipo key={tipo} tipo={tipo} base={base} habilitado={datos.carga_habilitada}
            inicial={datos.certificados.find(certificado => certificado.tipo === tipo)} />
        ))}
      </div>
    </>}
  </section>;
}
