import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage, mediaUrl } from '../../../../services/http';
import type { AdminPet, Page, Shelter } from '../../../../services/types';
import PosterMascota from './PosterMascota';
const initial = {
  nombre: '',
  especie: 'PERRO',
  raza: '',
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
export default function MascotasPanel({ shelter: suppliedShelter }: { shelter?: Shelter }) {
  const [items, setItems] = useState<AdminPet[]>([]);
  const [draft, setDraft] = useState(initial);
  const [id, setId] = useState<number | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [fileIsImproved, setFileIsImproved] = useState(false);
  const [improvedPhoto, setImprovedPhoto] = useState<{ dataUrl: string; base64: string; mimeType: string; name: string; originalUrl: string | null } | null>(null);
  const [inputVersion, setInputVersion] = useState(0);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [descriptionSuggestion, setDescriptionSuggestion] = useState('');
  const [suggestion, setSuggestion] = useState<{ etiqueta: string; explicacion: string; evidencia: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [page, setPage] = useState(1);
  const [next, setNext] = useState(false);
  const [detail, setDetail] = useState<AdminPet | null>(null);
  const [posterPet, setPosterPet] = useState<AdminPet | null>(null);
  const [loadedShelter, setLoadedShelter] = useState<Shelter | null>(suppliedShelter ?? null);
  const shelter = suppliedShelter ?? loadedShelter;
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
  useEffect(() => {
    if (suppliedShelter) { setLoadedShelter(suppliedShelter); return; }
    void django<Shelter>('/api/albergues/mi-perfil/')
      .then(setLoadedShelter)
      .catch(e => setError(errorMessage(e)));
  }, [suppliedShelter]);
  function reset() {
    setSuggestion(null);
    setDescriptionSuggestion('');
    setId(null);
    setDraft(initial);
    setFile(null);
    setFileIsImproved(false);
    setImprovedPhoto(null);
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
      if (fileIsImproved) form.append('foto_mejorada_confirmada', 'true');
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
  async function generateDescription() {
    setBusy(true); setError(''); setSuccess('');
    try {
      const result = await django<{ borrador: string }>('/api/mascotas/generar-descripcion-borrador/', {
        method: 'POST',
        body: json({
          ...draft,
          edad_anos: draft.edad_anos === '' ? null : Number(draft.edad_anos),
          edad_meses: draft.edad_meses === '' ? null : Number(draft.edad_meses),
        }),
      });
      setDescriptionSuggestion(result.borrador);
      setSuccess('Borrador generado. Puedes editarlo, descartarlo o aplicarlo al editor antes de guardar.');
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  async function suggestTemperament() {
    if (!id || file) { setError('Guarda primero el perfil y la foto actuales.'); return; }
    setBusy(true); setError(''); setSuggestion(null);
    try {
      const result = await django<{ etiqueta: string; explicacion: string; evidencia: string }>(`/api/mascotas/${id}/sugerir-temperamento/`, { method: 'POST', body: json({}) });
      setSuggestion(result);
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  async function improvePhoto() {
    if (!id) { setError('Guarda primero la mascota y su foto para proponer una mejora.'); return; }
    if (file) { setError('Guarda primero la nueva foto; la mejora usará la foto almacenada.'); return; }
    setBusy(true); setError(''); setSuccess(''); setImprovedPhoto(null);
    try {
      const result = await django<{ original: { url: string | null }; foto_mejorada: { data_url: string; base64: string; mime_type: string; nombre: string } }>(
        `/api/mascotas/${id}/mejorar-foto/`, { method: 'POST', body: json({}) }
      );
      setImprovedPhoto({
        dataUrl: result.foto_mejorada.data_url,
        base64: result.foto_mejorada.base64,
        mimeType: result.foto_mejorada.mime_type,
        name: result.foto_mejorada.nombre,
        originalUrl: result.original.url,
      });
      setSuccess('Vista previa generada. Compara el resultado y elige si quieres usarla. La foto original sigue guardada.');
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }
  function selectImprovedPhoto() {
    if (!improvedPhoto) return;
    const binary = atob(improvedPhoto.base64);
    const bytes = Uint8Array.from(binary, char => char.charCodeAt(0));
    setFile(new File([bytes], improvedPhoto.name, { type: improvedPhoto.mimeType }));
    setFileIsImproved(true);
    setInputVersion(v => v + 1);
    setSuccess('Foto mejorada seleccionada. Pulsa Guardar cambios para reemplazar la original.');
  }
  async function show(value: number, edit: boolean) {
    setBusy(true);
    setError('');
    try {
      const pet = await django<AdminPet>(`/api/mascotas/${value}/`);
      if (edit) {
        setSuggestion(null);
        setDescriptionSuggestion('');
        setId(pet.id);
        setDraft({
          nombre: pet.nombre,
          especie: pet.especie,
          raza: pet.raza ?? '',
          sexo: pet.sexo,
          tamanio: pet.tamanio,
          temperamento: pet.temperamento,
          energia: pet.energia,
          edad_anos: pet.edad_anos === null ? '' : String(pet.edad_anos),
          edad_meses: pet.edad_meses === null ? '' : String(pet.edad_meses),
          descripcion: pet.descripcion,
        });
        setFile(null);
        setFileIsImproved(false);
        setInputVersion(v => v + 1);
      } else setDetail(pet);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function showPoster(value: number) {
    setBusy(true); setError(''); setPosterPet(null);
    try { setPosterPet(await django<AdminPet>(`/api/mascotas/${value}/`)); }
    catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
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
      <form className="pet-edit-form"
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
          <label>
            Raza (opcional)
            <input maxLength={100} value={draft.raza} onChange={e => setDraft({ ...draft, raza: e.target.value })} />
          </label>
          <p className="muted">
            Deja la edad vacía cuando se desconozca. El comportamiento y energía desconocidos se
            registran como Por evaluar.
          </p>
          <label htmlFor="descripcion-mascota">
            Descripción de la mascota
            <textarea id="descripcion-mascota"
              value={draft.descripcion}
              onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
            />
          </label>
          <div className="pet-ai-tools">
            <p className="muted">{id
              ? 'La descripción IA usa los datos actuales del formulario. Para temperamento y mejora de foto se requiere la mascota y su foto guardadas.'
              : 'Puedes sugerir la descripción con los datos del formulario antes de guardar. Temperamento y mejora de foto se habilitan después de guardar la mascota y su fotografía.'}</p>
            <div className="pet-ai-tools__buttons">
              <button type="button" className="secondary" disabled={busy || !draft.nombre.trim()} onClick={() => void generateDescription()}>
                Sugerir descripción con IA
              </button>
              <button type="button" disabled={busy || !id || !!file} onClick={() => void suggestTemperament()}>Sugerir temperamento con Gemini</button>
              <button type="button" className="secondary" disabled={busy || !id || !!file} onClick={() => void improvePhoto()}>
                Proponer mejora de foto con IA
              </button>
            </div>
            {descriptionSuggestion && <section className="pet-ai-result" aria-label="Borrador de descripción IA">
              <div className="pet-ai-result__heading"><span className="pet-ai-result__eyebrow">Borrador de IA</span><h4>Revisa la descripción sugerida</h4><p className="muted">Puedes editarla antes de aplicarla a la descripción de la mascota.</p></div>
              <label>Borrador editable de Gemini<textarea value={descriptionSuggestion} maxLength={500} onChange={e => setDescriptionSuggestion(e.target.value)} /></label>
              <div className="pet-ai-result__actions"><button type="button" onClick={() => { setDraft(old => ({ ...old, descripcion: descriptionSuggestion })); setDescriptionSuggestion(''); }}>Aplicar descripción al editor</button>
              <button type="button" className="secondary" onClick={() => setDescriptionSuggestion('')}>Descartar borrador</button></div>
            </section>}
            {suggestion && <section className="pet-ai-result" aria-label="Sugerencia de temperamento"><div className="pet-ai-result__heading"><span className="pet-ai-result__eyebrow">Sugerencia pendiente</span><h4>{suggestion.etiqueta}</h4></div><p>{suggestion.explicacion}</p><p className="muted">Observación: {suggestion.evidencia || 'Sin evidencia conductual'}</p>
              <div className="pet-ai-result__actions">
              <button type="button" onClick={() => { setDraft(old => ({ ...old, temperamento: suggestion.etiqueta })); setSuggestion(null); setSuccess('Etiqueta aplicada al editor. Revisa y guarda para confirmar.'); }}>Aplicar al editor</button>
              <button type="button" className="secondary" onClick={() => setSuggestion(null)}>Descartar sugerencia</button></div></section>}
            <p className="muted pet-ai-tools__note">Las sugerencias no se guardan automáticamente. Revisa los cambios y confirma con “Guardar cambios de mascota”.</p>
          </div>
          <label>
            {id ? 'Reemplazar fotografía (opcional)' : 'Fotografía JPEG/PNG, máximo 5 MB'}
            <input
              key={inputVersion}
              type="file"
              accept="image/jpeg,image/png"
              required={!id}
              onChange={e => { setFile(e.target.files?.[0] ?? null); setFileIsImproved(false); setImprovedPhoto(null); }}
            />
          </label>
          {id && <p className="muted pet-photo-note">La mejora aparece como vista previa; la original se conserva hasta que guardes.</p>}
          {improvedPhoto && <div className="confirmation">
            <h4>Comparar fotografía</h4>
            <div className="grid">
              {improvedPhoto.originalUrl && <figure>
                <figcaption>Original</figcaption>
                <img className="detail-photo" src={mediaUrl(improvedPhoto.originalUrl)} alt="Fotografía original" />
              </figure>}
              <figure>
                <figcaption>Propuesta de IA</figcaption>
                <img className="detail-photo" src={improvedPhoto.dataUrl} alt="Vista previa de fotografía mejorada" />
              </figure>
            </div>
            <button type="button" className="secondary" onClick={selectImprovedPhoto}>Usar esta foto</button>
          </div>}
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
            <button disabled={busy || !p.foto_principal || !shelter || !shelter.nombre.trim() || !(shelter.telefono.trim() || shelter.email.trim())}
              className="secondary" onClick={() => void showPoster(p.id)}>
              Preparar afiche
            </button>
            {!p.foto_principal && <span className="muted">Añade una foto para generar el afiche.</span>}
            {shelter && !(shelter.telefono.trim() || shelter.email.trim()) && <span className="muted">Completa un teléfono o correo público del albergue.</span>}
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
      {posterPet && shelter && <PosterMascota pet={posterPet} shelter={shelter} onClose={() => setPosterPet(null)} />}
    </section>
  );
}
