import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage, mediaUrl } from '../../../../services/http';
import type { AdminPet, Page } from '../../../../services/types';
const initial = {
  nombre: '',
  especie: 'PERRO',
  sexo: 'POR_EVAL',
  tamanio: '',
  temperamento: 'POR_EVALUAR',
  energia: 'POR_EVALUAR',
  edad_anos: '',
  edad_meses: '',
  descripcion: '',
};
const choices = {
  especie: ['PERRO', 'GATO', 'OTRO'],
  sexo: ['POR_EVAL', 'HEMBRA', 'MACHO'],
  tamanio: ['', 'PEQUENO', 'MEDIANO', 'GRANDE'],
  temperamento: [
    'POR_EVALUAR',
    'TRANQUILO',
    'ACTIVO',
    'JUGUETON',
    'TIMIDO',
    'SOCIABLE',
    'INDEPENDIENTE',
  ],
  energia: ['POR_EVALUAR', 'BAJA', 'MEDIA', 'ALTA'],
};
const labels = {
  especie: 'Especie',
  sexo: 'Sexo',
  tamanio: 'Tamaño',
  temperamento: 'Temperamento',
  energia: 'Energía',
};
export default function MascotasPanel() {
  const [items, setItems] = useState<AdminPet[]>([]);
  const [draft, setDraft] = useState(initial);
  const [id, setId] = useState<number | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [inputVersion, setInputVersion] = useState(0);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState(false);
  const [page, setPage] = useState(1);
  const [next, setNext] = useState(false);
  const [detail, setDetail] = useState<AdminPet | null>(null);
  const load = useCallback(async () => {
    setError('');
    try {
      const result = await django<Page<AdminPet>>(`/api/mascotas/?page=${page}`);
      setItems(result.results);
      setNext(result.next !== null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [page]);
  useEffect(() => {
    void load();
  }, [load]);
  function reset() {
    setId(null);
    setDraft(initial);
    setFile(null);
    setInputVersion(v => v + 1);
  }
  async function save() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      if (!id && !file) throw new Error('Agrega una fotografía JPEG o PNG.');
      if (file && (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024))
        throw new Error('La fotografía debe ser JPEG/PNG y no superar 5 MB.');
      const form = new FormData();
      Object.entries(draft).forEach(([k, v]) => {
        if (k !== 'edad_anos' && k !== 'edad_meses') form.append(k, v);
      });
      if (file) form.append('foto_principal', file);
      form.append('edad_anos', draft.edad_anos);
      form.append('edad_meses', draft.edad_meses);
      await django(id ? `/api/mascotas/${id}/` : '/api/mascotas/', {
        method: id ? 'PATCH' : 'POST',
        body: form,
      });
      reset();
      setSuccess('Mascota guardada.');
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function show(value: number, edit: boolean) {
    setBusy(true);
    setError('');
    try {
      const pet = await django<AdminPet>(`/api/mascotas/${value}/`);
      if (edit) {
        setId(pet.id);
        setDraft({
          nombre: pet.nombre,
          especie: pet.especie,
          sexo: pet.sexo,
          tamanio: pet.tamanio,
          temperamento: pet.temperamento,
          energia: pet.energia,
          edad_anos: pet.edad_anos === null ? '' : String(pet.edad_anos),
          edad_meses: pet.edad_meses === null ? '' : String(pet.edad_meses),
          descripcion: pet.descripcion,
        });
        setFile(null);
        setInputVersion(v => v + 1);
      } else setDetail(pet);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove(pet: AdminPet) {
    if (!window.confirm(`¿Eliminar a ${pet.nombre}? Esta operación no se puede deshacer.`)) return;
    setBusy(true);
    setError('');
    try {
      await django(`/api/mascotas/${pet.id}/`, { method: 'DELETE' });
      if (id === pet.id) reset();
      if (items.length === 1 && page > 1) setPage(v => v - 1);
      else await load();
      setSuccess('Mascota eliminada.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function state(pet: AdminPet, estado: string) {
    if (!window.confirm(`¿Cambiar a ${pet.nombre} a ${estado.replaceAll('_', ' ')}?`)) return;
    setBusy(true);
    setError('');
    try {
      await django(`/api/mascotas/${pet.id}/cambiar-estado/`, {
        method: 'POST',
        body: json({ estado }),
      });
      await load();
      setSuccess('Disponibilidad actualizada.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Mascotas de mi albergue</h2>
      <Feedback error={error} success={success} />
      <form
        onSubmit={e => {
          e.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={busy}>
          <h3>{id ? 'Editar mascota' : 'Registrar mascota'}</h3>
          <div className="grid">
            <label>
              Nombre de la mascota
              <input
                required
                maxLength={100}
                value={draft.nombre}
                onChange={e => setDraft({ ...draft, nombre: e.target.value })}
              />
            </label>
            {(Object.keys(choices) as (keyof typeof choices)[]).map(key => (
              <label key={key}>
                {labels[key]}
                <select
                  aria-label={labels[key]}
                  value={draft[key]}
                  onChange={e => setDraft({ ...draft, [key]: e.target.value })}
                >
                  {choices[key].map(v => (
                    <option value={v} key={v}>
                      {v === '' || v === 'POR_EVAL' ? 'Por evaluar' : v.replaceAll('_', ' ')}
                    </option>
                  ))}
                </select>
              </label>
            ))}
            <label>
              Edad: años
              <input
                type="number"
                min={0}
                max={50}
                step={1}
                value={draft.edad_anos}
                onChange={e => setDraft({ ...draft, edad_anos: e.target.value })}
              />
            </label>
            <label>
              Edad: meses adicionales
              <input
                type="number"
                min={0}
                max={11}
                step={1}
                value={draft.edad_meses}
                onChange={e => setDraft({ ...draft, edad_meses: e.target.value })}
              />
            </label>
          </div>
          <p className="muted">
            Deja la edad vacía cuando se desconozca. El comportamiento y energía desconocidos se
            registran como Por evaluar.
          </p>
          <label>
            Descripción de la mascota
            <textarea
              value={draft.descripcion}
              onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
            />
          </label>
          <label>
            {id ? 'Reemplazar fotografía (opcional)' : 'Fotografía JPEG/PNG, máximo 5 MB'}
            <input
              key={inputVersion}
              type="file"
              accept="image/jpeg,image/png"
              required={!id}
              onChange={e => setFile(e.target.files?.[0] ?? null)}
            />
          </label>
          <div className="actions">
            <button>{id ? 'Guardar cambios de mascota' : 'Registrar mascota'}</button>
            {id && (
              <button type="button" className="secondary" onClick={reset}>
                Cancelar edición
              </button>
            )}
          </div>
        </fieldset>
      </form>
      {items.length === 0 && <p>Aún no hay mascotas registradas.</p>}
      {items.map(p => (
        <article key={p.id} className="list-row">
          <div>
            {p.foto_principal && (
              <img className="thumbnail" src={mediaUrl(p.foto_principal)} alt={p.nombre} />
            )}
            <h3>{p.nombre}</h3>
            <span className="badge">{p.estado.replaceAll('_', ' ')}</span>
          </div>
          <div className="actions">
            <button disabled={busy} className="secondary" onClick={() => void show(p.id, false)}>
              Ver mascota
            </button>
            <button disabled={busy} className="secondary" onClick={() => void show(p.id, true)}>
              Editar mascota
            </button>
            <button disabled={busy} className="danger" onClick={() => void remove(p)}>
              Eliminar mascota
            </button>
            {['DISPONIBLE', 'EN_PROCESO', 'ADOPTADO']
              .filter(s => s !== p.estado)
              .map(s => (
                <button disabled={busy} key={s} onClick={() => void state(p, s)}>
                  Cambiar a {s.replaceAll('_', ' ')}
                </button>
              ))}
          </div>
        </article>
      ))}
      <div className="actions">
        <button disabled={busy || page === 1} onClick={() => setPage(v => v - 1)}>
          Página anterior
        </button>
        <span>Página {page}</span>
        <button disabled={busy || !next} onClick={() => setPage(v => v + 1)}>
          Página siguiente
        </button>
        <button disabled={busy} className="secondary" onClick={() => void load()}>
          Actualizar mascotas
        </button>
      </div>
      {detail && (
        <article className="confirmation">
          <h3>{detail.nombre}</h3>
          {detail.foto_principal && (
            <img
              className="detail-photo"
              src={mediaUrl(detail.foto_principal)}
              alt={detail.nombre}
            />
          )}
          <p>{detail.descripcion}</p>
          <p>
            {detail.especie} · {detail.tamanio} · {detail.temperamento} · {detail.energia}
          </p>
          <p>
            Edad: {detail.edad_anos ?? 'Desconocida'} años, {detail.edad_meses ?? 'Desconocidos'}{' '}
            meses
          </p>
          <p>{detail.estado}</p>
          <button onClick={() => setDetail(null)}>Cerrar detalle</button>
        </article>
      )}
    </section>
  );
}
