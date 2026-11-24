import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { errorMessage } from '../../../../services/http';
import {
  actualizarConfiguracionGlobal,
  obtenerConfiguracionGlobal,
  type ConfiguracionGlobal,
} from '../../../../services/configuracionGlobal';

export default function ConfiguracionGlobalPage() {
  const [configuracion, setConfiguracion] = useState<ConfiguracionGlobal | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const cargar = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setConfiguracion(await obtenerConfiguracionGlobal(true));
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void cargar(); }, [cargar]);

  async function guardar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!configuracion) return;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const actualizada = await actualizarConfiguracionGlobal({
        maxImagenMb: configuracion.maxImagenMb,
        expiracionSesionDias: configuracion.expiracionSesionDias,
      });
      setConfiguracion(actualizada);
      setSuccess('Configuración guardada. El límite de imágenes se actualiza en los formularios y la duración aplica a las nuevas sesiones.');
    } catch (cause) {
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  }

  return <main className="card">
    <header>
      <p className="eyebrow">Administración Petly</p>
      <h1>Configuración global</h1>
      <p className="muted">Define límites compartidos por las funciones de Petly.</p>
    </header>
    <Feedback error={error} success={success} />
    {loading ? <p role="status">Cargando configuración…</p> : configuracion ? <form onSubmit={event => void guardar(event)}>
      <fieldset disabled={busy}>
        <label htmlFor="config-max-imagen">Tamaño máximo por imagen (MB)
          <input id="config-max-imagen" type="number" min={1} max={20} step={1} required
            value={configuracion.maxImagenMb}
            onChange={event => setConfiguracion({ ...configuracion, maxImagenMb: Number(event.target.value) })} />
        </label>
        <p className="muted">Se aplica a fotos de mascotas, documentos de identidad, reportes de bienestar e historias de adopción.</p>
        <label htmlFor="config-expiracion-sesion">Duración de nuevas sesiones (días)
          <input id="config-expiracion-sesion" type="number" min={1} max={365} step={1} required
            value={configuracion.expiracionSesionDias}
            onChange={event => setConfiguracion({ ...configuracion, expiracionSesionDias: Number(event.target.value) })} />
        </label>
        <p className="muted">Las sesiones ya iniciadas conservan su fecha de expiración actual.</p>
        {configuracion.actualizadoEn && <p className="muted">Última actualización: {new Date(configuracion.actualizadoEn).toLocaleString('es-PE')}</p>}
        <div className="actions">
          <button type="submit" disabled={busy || !Number.isInteger(configuracion.maxImagenMb) || !Number.isInteger(configuracion.expiracionSesionDias)}>
            {busy ? 'Guardando…' : 'Guardar configuración'}
          </button>
          <button type="button" className="secondary" disabled={busy} onClick={() => void cargar()}>Recargar</button>
        </div>
      </fieldset>
    </form> : <button type="button" className="secondary" onClick={() => void cargar()}>Reintentar</button>}
  </main>;
}
