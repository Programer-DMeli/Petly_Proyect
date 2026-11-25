import { useEffect, useRef, useState } from 'react';
import AsistenteRespuesta from '../../../../components/AsistenteRespuesta';
import Feedback from '../../../../components/Feedback';
import { errorMessage, json, spring } from '../../../../services/http';
import type { Pet } from '../../../../services/types';
import './ayuda-para-adoptar.css';

type Pregunta = { id: string; pregunta: string; respuesta: string };
type Categoria = { id: string; nombre: string; preguntas: Pregunta[] };
type Cuidados = { mensaje: string; consejos: { categoria: string; titulo: string; contenido: string }[] };

export default function AyudaParaAdoptar({ mascotas }: { mascotas: Pet[] }) {
  return <section className="ayuda-adopcion" aria-labelledby="ayuda-adopcion-titulo">
    <h2 id="ayuda-adopcion-titulo">Ayuda para adoptar</h2>
    <div className="ayuda-adopcion__columnas">
      <PreguntasFrecuentes />
      <CuidadosMascota mascotas={mascotas} />
    </div>
  </section>;
}

function PreguntasFrecuentes() {
  const [categorias, setCategorias] = useState<Categoria[] | null>(null);
  const [error, setError] = useState('');
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setError('');
    void spring<Categoria[]>('/api/asistente/preguntas-frecuentes', { signal: controller.signal })
      .then(datos => { if (!controller.signal.aborted) setCategorias(datos); })
      .catch(cause => { if (!controller.signal.aborted) setError(errorMessage(cause)); });
    return () => controller.abort();
  }, [intento]);

  return <article className="ayuda-adopcion__articulo" aria-labelledby="ayuda-preguntas-titulo">
    <h3 id="ayuda-preguntas-titulo">Preguntas frecuentes</h3>
    <Feedback error={error} />
    {error && <button type="button" className="secondary" onClick={() => setIntento(valor => valor + 1)}>Reintentar</button>}
    {!categorias && !error && <p role="status">Cargando preguntas…</p>}
    {categorias?.every(categoria => !categoria.preguntas.length) && <p>Aún no hay preguntas publicadas.</p>}
    {categorias?.filter(categoria => categoria.preguntas.length > 0).map(categoria => (
      <div className="ayuda-adopcion__categoria" key={categoria.id}>
        <h4>{categoria.nombre}</h4>
        {categoria.preguntas.map(item => <RespuestaPregunta key={item.id} item={item} />)}
      </div>
    ))}
  </article>;
}

function RespuestaPregunta({ item }: { item: Pregunta }) {
  const [respuesta, setRespuesta] = useState('');
  const [respaldo, setRespaldo] = useState(false);
  const [cargando, setCargando] = useState(false);
  const peticion = useRef<AbortController | null>(null);
  useEffect(() => () => {
    const activa = peticion.current;
    peticion.current = null;
    activa?.abort();
  }, []);

  async function consultar() {
    if (peticion.current) return;
    const controller = new AbortController();
    peticion.current = controller;
    setCargando(true);
    const timeout = window.setTimeout(() => controller.abort(), 65_000);
    try {
      const resultado = await spring<{ reply: string }>('/api/asistente/chat', {
        method: 'POST', body: json({ messages: [{ role: 'user', text: item.pregunta }], platform: 'web' }),
        signal: controller.signal,
      });
      if (peticion.current !== controller) return;
      if (!resultado.reply?.trim()) throw new Error('Respuesta vacía');
      setRespuesta(resultado.reply);
      setRespaldo(false);
    } catch {
      if (peticion.current !== controller) return;
      setRespuesta(item.respuesta);
      setRespaldo(true);
    } finally {
      window.clearTimeout(timeout);
      if (peticion.current === controller) {
        peticion.current = null;
        setCargando(false);
      }
    }
  }

  return <details onToggle={event => {
    if (event.currentTarget.open && !respuesta) void consultar();
  }}>
    <summary>{item.pregunta}</summary>
    <div className="ayuda-adopcion__respuesta" aria-live="polite">
      {cargando ? <p role="status">El asistente está preparando la respuesta…</p> : respuesta && <>
        <AsistenteRespuesta text={respuesta} />
        {respaldo && <>
          <p className="muted">Respuesta guardada. El asistente no está disponible en este momento.</p>
          <button type="button" className="secondary" onClick={() => void consultar()}>Reintentar con el asistente</button>
        </>}
      </>}
    </div>
  </details>;
}

function CuidadosMascota({ mascotas }: { mascotas: Pet[] }) {
  const [seleccion, setSeleccion] = useState('');
  const mascota = mascotas.find(item => String(item.id) === seleccion);

  return <article className="ayuda-adopcion__articulo" aria-labelledby="ayuda-cuidados-titulo">
    <h3 id="ayuda-cuidados-titulo">Cuidados por mascota</h3>
    {mascotas.length ? <label>Mascota del catálogo
      <select value={mascota ? seleccion : ''} onChange={event => setSeleccion(event.target.value)}>
        <option value="">Selecciona una mascota</option>
        {mascotas.map(item => <option key={item.id} value={item.id}>{item.nombre} · {item.albergue}</option>)}
      </select>
    </label> : <p>Cuando aparezcan mascotas en el catálogo, podrás consultar aquí sus cuidados.</p>}
    {mascota && <FichaCuidados key={mascota.id} mascota={mascota} />}
  </article>;
}

function FichaCuidados({ mascota }: { mascota: Pet }) {
  const [datos, setDatos] = useState<Cuidados | null>(null);
  const [error, setError] = useState('');
  const [intento, setIntento] = useState(0);
  const especie = mascota.especie.toLocaleLowerCase();
  const raza = mascota.raza?.trim();
  const disponible = especie === 'perro' || especie === 'gato';

  useEffect(() => {
    if (!disponible) return;
    const controller = new AbortController();
    const query = new URLSearchParams({ especie });
    if (raza) query.set('raza', raza);
    setError('');
    void spring<Cuidados>(`/api/asistente/cuidados?${query}`, { signal: controller.signal })
      .then(resultado => { if (!controller.signal.aborted) setDatos(resultado); })
      .catch(cause => { if (!controller.signal.aborted) setError(errorMessage(cause)); });
    return () => controller.abort();
  }, [disponible, especie, raza, intento]);

  return <div className="ayuda-adopcion__cuidados" aria-live="polite">
    <h4>{mascota.nombre}</h4>
    <p>{mascota.especie}{raza && ` · ${raza}`}</p>
    {!disponible && <p>Aún no hay cuidados publicados para esta especie.</p>}
    {disponible && !datos && !error && <p role="status">Consultando cuidados…</p>}
    <Feedback error={error} />
    {error && <button type="button" className="secondary" onClick={() => setIntento(valor => valor + 1)}>Reintentar</button>}
    {datos && <>
      <p>{datos.consejos.length ? datos.mensaje : 'Aún no hay cuidados publicados para esta mascota.'}</p>
      <ul>{datos.consejos.map(consejo => <li key={`${consejo.categoria}-${consejo.titulo}`}>
        <strong>{consejo.titulo}</strong>
        <p>{consejo.contenido}</p>
      </li>)}</ul>
    </>}
  </div>;
}
