import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';
import { useMaxImagenMb } from '../../../../services/useMaxImagenMb';
import FotoCasoExitoPrivada, { type FotoCasoExito } from '../../../shared/FotoCasoExitoPrivada';

type AdopcionDisponible = {
  id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  fecha_entrega: string;
};

type CasoExito = {
  id: number;
  solicitud_id: number;
  mascota_nombre: string;
  albergue_nombre: string;
  historia: string;
  estado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
  estado_display: string;
  motivo_revision: string;
  creado_en: string;
  fotos: FotoCasoExito[];
};
type Coleccion<T> = T[] | { results: T[]; next: string | null };

const API = '/api/contratos/casos-exito/';
const MAX_FOTOS = 5;
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;

function rows<T>(data: Coleccion<T>) {
  return Array.isArray(data) ? data : data.results;
}

function fecha(value: string) {
  const date = new Date(`${value}T12:00:00`);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(date);
}

async function cargarHistorias(): Promise<CasoExito[]> {
  const todas: CasoExito[] = [];
  let pagina = 1;
  let siguiente: string | null;
  do {
    const respuesta = await django<Coleccion<CasoExito>>(`${API}?page=${pagina}&page_size=100`);
    todas.push(...rows(respuesta));
    siguiente = Array.isArray(respuesta) ? null : respuesta.next;
    pagina += 1;
  } while (siguiente);
  return todas;
}

export default function CasosExitoPage() {
  const maxImagenMb = useMaxImagenMb();
  const [adopciones, setAdopciones] = useState<AdopcionDisponible[]>([]);
  const [historias, setHistorias] = useState<CasoExito[]>([]);
  const [solicitudId, setSolicitudId] = useState('');
  const [historia, setHistoria] = useState('');
  const [fotos, setFotos] = useState<File[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [disponibles, propias] = await Promise.all([
        django<AdopcionDisponible[]>(`${API}adopciones-disponibles/`),
        cargarHistorias(),
      ]);
      setAdopciones(disponibles);
      setHistorias(propias);
      setSolicitudId(current => disponibles.some(item => String(item.id) === current)
        ? current
        : String(disponibles[0]?.id ?? ''));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void cargar(); }, [cargar]);

  function seleccionarFotos(files: FileList | null) {
    const seleccion = Array.from(files ?? []);
    const total = seleccion.reduce((suma, file) => suma + file.size, 0);
    if (seleccion.length > MAX_FOTOS) {
      setError('Puedes adjuntar hasta cinco fotos.');
      setFotos([]);
      return;
    }
    if (seleccion.some(file => !['image/jpeg', 'image/png'].includes(file.type)
      || file.size <= 0 || file.size > maxImagenMb * 1024 * 1024)) {
      setError(`Cada foto debe ser JPEG o PNG y pesar hasta ${maxImagenMb} MB.`);
      setFotos([]);
      return;
    }
    if (total > MAX_TOTAL_BYTES) {
      setError('Las fotos juntas no pueden superar 20 MB.');
      setFotos([]);
      return;
    }
    setFotos(seleccion);
    setError('');
    setSuccess('');
  }

  async function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formulario = event.currentTarget;
    const texto = historia.trim();
    if (!solicitudId) { setError('Selecciona una adopción entregada.'); return; }
    if (texto.length < 20) { setError('Cuéntanos un poco más: escribe al menos 20 caracteres.'); return; }
    if (fotos.length === 0) { setError('Adjunta al menos una foto de tu historia.'); return; }

    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const body = new FormData();
      body.set('solicitud_id', solicitudId);
      body.set('historia', texto);
      fotos.forEach(foto => body.append('fotos', foto));
      await django<CasoExito>(API, { method: 'POST', body });
      setHistoria('');
      setFotos([]);
      formulario.reset();
      setSuccess('Tu historia se envió y quedó pendiente de revisión.');
      await cargar();
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return <section className="card">
    <header>
      <p className="eyebrow">Comunidad Petly</p>
      <h1>Comparte tu historia de adopción</h1>
      <p className="muted">Cuéntale a la comunidad cómo es tu vida con tu nuevo compañero. Las historias se revisan antes de publicarse.</p>
      <p><Link to="/historias-exito">Conoce las historias publicadas por la comunidad</Link></p>
    </header>

    <Feedback error={error} success={success} />
    {loading ? <p role="status">Cargando tus adopciones…</p> : <>
      {adopciones.length > 0 ? <form onSubmit={event => void enviar(event)}>
        <fieldset disabled={busy}>
          <label htmlFor="caso-exito-adopcion">Adopción entregada
            <select id="caso-exito-adopcion" required value={solicitudId}
              onChange={event => setSolicitudId(event.target.value)}>
              {adopciones.map(adopcion => <option key={adopcion.id} value={adopcion.id}>
                {adopcion.mascota_nombre} · {adopcion.albergue_nombre} · entrega {fecha(adopcion.fecha_entrega)}
              </option>)}
            </select>
          </label>
          <label htmlFor="caso-exito-historia">Tu historia
            <textarea id="caso-exito-historia" required minLength={20} maxLength={4000}
              value={historia} onChange={event => setHistoria(event.target.value)}
              placeholder="¿Cómo fue la llegada a casa? ¿Qué momento especial quisieras compartir?" />
          </label>
          <label htmlFor="caso-exito-fotos">Fotos de tu historia (JPEG o PNG; hasta 5 fotos de {maxImagenMb} MB cada una)
            <input id="caso-exito-fotos" type="file" accept="image/jpeg,image/png" multiple required
              onChange={event => seleccionarFotos(event.currentTarget.files)} />
          </label>
          {fotos.length > 0 && <p>{fotos.map(file => file.name).join(' · ')}</p>}
          <p className="muted">Las fotos se guardan de forma privada mientras tu historia está pendiente de revisión.</p>
          <button disabled={busy || !solicitudId || fotos.length === 0}>
            {busy ? 'Enviando…' : 'Enviar historia para revisión'}
          </button>
        </fieldset>
      </form> : <p>No tienes adopciones entregadas disponibles para enviar otra historia.</p>}

      <section aria-labelledby="mis-historias-titulo">
        <h2 id="mis-historias-titulo">Mis historias enviadas</h2>
        {historias.length === 0 ? <p>Aún no has enviado una historia de adopción.</p> : <div className="list-stack">
          {historias.map(caso => <article className="card" key={caso.id}>
            <h3>{caso.mascota_nombre}</h3>
            <p className="muted">Adopción con {caso.albergue_nombre} · enviada {new Date(caso.creado_en).toLocaleDateString('es-PE')}</p>
            <p><strong>Estado:</strong> {caso.estado_display}</p>
            <p>{caso.historia}</p>
            <p>{caso.fotos.length} {caso.fotos.length === 1 ? 'foto adjunta' : 'fotos adjuntas'}</p>
            {caso.estado === 'RECHAZADO' && caso.motivo_revision &&
              <p className="feedback error"><strong>Motivo:</strong> {caso.motivo_revision}</p>}
            <div className="case-story-photos">
              {caso.fotos.map(foto => <FotoCasoExitoPrivada key={foto.id} foto={foto}
                alt={`Foto privada de tu historia de ${caso.mascota_nombre}`} />)}
            </div>
          </article>)}
        </div>}
      </section>
    </>}
  </section>;
}
