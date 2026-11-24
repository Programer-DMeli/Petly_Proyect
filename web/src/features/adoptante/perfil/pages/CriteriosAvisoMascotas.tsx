import { useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { errorMessage } from '../../../../services/http';
import {
  getCriteriosAviso,
  guardarCriteriosAviso,
  type CriteriosAviso,
} from '../../../../services/notificacionesApi';

const filtros = [
  { campo: 'especie', etiqueta: 'Especie', opciones: [
    ['PERRO', 'Perro'], ['GATO', 'Gato'], ['OTRO', 'Otro'],
  ] },
  { campo: 'tamanio', etiqueta: 'Tamaño', opciones: [
    ['PEQUENO', 'Pequeño'], ['MEDIANO', 'Mediano'], ['GRANDE', 'Grande'],
  ] },
  { campo: 'temperamento', etiqueta: 'Temperamento', opciones: [
    ['TRANQUILO', 'Tranquilo'], ['ACTIVO', 'Activo'], ['JUGUETON', 'Juguetón'],
    ['TIMIDO', 'Tímido'], ['SOCIABLE', 'Sociable'], ['INDEPENDIENTE', 'Independiente'],
    ['POR_EVALUAR', 'Por evaluar'],
  ] },
] as const;

const edades = [
  { campo: 'edad_min_meses', etiqueta: 'Edad mínima (meses)' },
  { campo: 'edad_max_meses', etiqueta: 'Edad máxima (meses)' },
] as const;

export default function CriteriosAvisoMascotas() {
  const [criterios, setCriterios] = useState<CriteriosAviso | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    getCriteriosAviso().then(datos => {
      if (vigente) setCriterios(datos);
    }).catch(causa => {
      if (vigente) setError(errorMessage(causa));
    });
    return () => { vigente = false; };
  }, [intento]);

  function editar(cambios: Partial<CriteriosAviso>) {
    setCriterios(actual => actual && { ...actual, ...cambios });
    setError('');
    setSuccess('');
  }

  async function guardar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!criterios || busy) return;
    setSuccess('');
    const { edad_min_meses, edad_max_meses } = criterios;
    if (edad_min_meses !== null && edad_max_meses !== null && edad_min_meses > edad_max_meses) {
      setError('La edad mínima no puede superar la máxima.');
      return;
    }
    setError('');
    setBusy(true);
    try {
      const guardados = await guardarCriteriosAviso(criterios);
      setCriterios(guardados);
      setSuccess(guardados.activo ? 'Avisos activados en web y Android.' : 'Criterios guardados con los avisos desactivados.');
    } catch (causa) {
      setError(errorMessage(causa));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card" aria-labelledby="aviso-mascotas">
      <h2 id="aviso-mascotas">Avisos de nuevas mascotas</h2>
      <p>Con los avisos activos, las nuevas mascotas aparecen en tu bandeja. Puedes elegir filtros para recibir solo las que cumplan todos tus criterios.</p>
      <Feedback error={error} success={success} />
      {criterios ? (
        <form onSubmit={evento => void guardar(evento)}>
          <fieldset disabled={busy}>
            <label>
              <input type="checkbox" checked={criterios.activo} onChange={e => editar({ activo: e.target.checked })} />
              Activar avisos de nuevas mascotas
            </label>
            <div className="grid">
              {filtros.map(({ campo, etiqueta, opciones }) => (
                <label key={campo}>
                  {etiqueta}
                  <select value={criterios[campo]} onChange={e => editar({ [campo]: e.target.value })}>
                    <option value="">Cualquiera</option>
                    {opciones.map(([valor, texto]) => <option key={valor} value={valor}>{texto}</option>)}
                  </select>
                </label>
              ))}
              {edades.map(({ campo, etiqueta }) => (
                <label key={campo}>
                  {etiqueta}
                  <input type="number" min={0} max={600} step={1} value={criterios[campo] ?? ''}
                    onChange={e => editar({ [campo]: e.target.value === '' ? null : e.target.valueAsNumber })} />
                </label>
              ))}
            </div>
            <button type="submit">Guardar criterios</button>
          </fieldset>
        </form>
      ) : error ? (
        <button type="button" onClick={() => { setError(''); setIntento(intento + 1); }}>Reintentar</button>
      ) : (
        <p role="status">Cargando criterios…</p>
      )}
    </section>
  );
}
