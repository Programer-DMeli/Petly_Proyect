import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';
import type { Infrastructure } from '../../../../services/types';
const types = [
  'RECEPCION',
  'OFICINA',
  'ZONA_CUARENTENA',
  'ZONA_CACHORROS',
  'ZONA_ADULTOS',
  'ZONA_GATOS',
  'ENFERMERIA',
  'QUIROFANO',
  'ALMACEN',
  'PATIO',
  'BAÑOS',
  'OTRO',
];
const initial = {
  tipo: 'PATIO',
  nombre: '',
  descripcion: '',
  capacidad: '',
  metros_cuadrados: '',
  equipamiento: '',
  activo: true,
};
type InfraestructuraPanelProps = {
  shelterId: number;
  onComplete?: (force?: boolean) => void;
};
export default function InfraestructuraPanel({ shelterId, onComplete }: InfraestructuraPanelProps) {
  const [items, setItems] = useState<Infrastructure[]>([]);
  const [draft, setDraft] = useState(initial);
  const [id, setId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState('');
  const path = `/api/albergues/${shelterId}/infraestructura/`;
  const load = useCallback(async () => {
    try {
      const result = await django<Infrastructure[]>(path);
      setItems(result);
      if (result.length > 0) onComplete?.();
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [onComplete, path]);
  useEffect(() => {
    void load();
  }, [load]);
  async function save() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const saved = await django<Infrastructure>(id ? path + `${id}/` : path, {
        method: id ? 'PATCH' : 'POST',
        body: json({
          ...draft,
          capacidad: draft.capacidad === '' ? null : Number(draft.capacidad),
          metros_cuadrados: draft.metros_cuadrados === '' ? null : draft.metros_cuadrados,
        }),
      });
      setItems(current =>
        id ? current.map(item => (item.id === saved.id ? saved : item)) : [...current, saved]
      );
      setDraft(initial);
      setId(null);
      setSuccess('Área guardada.');
      if (onComplete) {
        onComplete(true);
      } else {
        await load();
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  async function remove(value: number) {
    if (!window.confirm('¿Eliminar esta área de infraestructura?')) return;
    setBusy(true);
    setError('');
    try {
      await django(path + `${value}/`, { method: 'DELETE' });
      if (id === value) {
        setId(null);
        setDraft(initial);
      }
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <h2>Infraestructura</h2>
      <Feedback error={error} success={success} />
      <form
        onSubmit={e => {
          e.preventDefault();
          void save();
        }}
      >
        <fieldset disabled={busy}>
          <div className="grid">
            <label>
              Tipo de área
              <select
                value={draft.tipo}
                onChange={e => setDraft({ ...draft, tipo: e.target.value })}
              >
                {types.map(t => (
                  <option key={t} value={t}>
                    {t.replaceAll('_', ' ')}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Nombre del área
              <input
                required
                maxLength={100}
                value={draft.nombre}
                onChange={e => setDraft({ ...draft, nombre: e.target.value })}
              />
            </label>
            <label>
              Capacidad del área
              <input
                type="number"
                min={0}
                step={1}
                value={draft.capacidad}
                onChange={e => setDraft({ ...draft, capacidad: e.target.value })}
              />
            </label>
            <label>
              Metros cuadrados
              <input
                type="number"
                min={0}
                max={999999.99}
                step="0.01"
                value={draft.metros_cuadrados}
                onChange={e => setDraft({ ...draft, metros_cuadrados: e.target.value })}
              />
            </label>
          </div>
          <label>
            Descripción del área
            <textarea
              value={draft.descripcion}
              onChange={e => setDraft({ ...draft, descripcion: e.target.value })}
            />
          </label>
          <label>
            Equipamiento
            <textarea
              value={draft.equipamiento}
              onChange={e => setDraft({ ...draft, equipamiento: e.target.value })}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={draft.activo}
              onChange={e => setDraft({ ...draft, activo: e.target.checked })}
            />{' '}
            Área activa
          </label>
          <div className="actions">
            <button>{id ? 'Actualizar área' : 'Agregar área'}</button>
            {id && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setId(null);
                  setDraft(initial);
                }}
              >
                Cancelar edición
              </button>
            )}
          </div>
        </fieldset>
      </form>
      {items.map(v => (
        <article key={v.id} className="list-row">
          <div>
            <h3>{v.nombre}</h3>
            <p>
              {v.tipo.replaceAll('_', ' ')} · {v.capacidad ?? 'Capacidad sin indicar'} ·{' '}
              {v.activo ? 'Activa' : 'Inactiva'}
            </p>
          </div>
          <div className="actions">
            <button
              disabled={busy}
              className="secondary"
              onClick={() => {
                setId(v.id);
                setDraft({
                  ...v,
                  capacidad: v.capacidad === null ? '' : String(v.capacidad),
                  metros_cuadrados: v.metros_cuadrados ?? '',
                });
              }}
            >
              Editar área
            </button>
            <button disabled={busy} className="danger" onClick={() => void remove(v.id)}>
              Eliminar área
            </button>
          </div>
        </article>
      ))}
    </section>
  );
}
