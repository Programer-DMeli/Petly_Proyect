import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { clearSessionLocally } from '../../../../services/session';
import { errorMessage } from '../../../../services/http';
import {
  consultarOperacion,
  crearIdOperacion,
  descargarJson,
  exportarDatos,
  solicitarBaja,
  type OperacionPrivacidad,
} from '../../../../services/privacidadApi';
import './privacidad.css';

const OPERACION_PENDIENTE = 'petly.privacidad.baja';

export default function PrivacidadPage() {
  const navigate = useNavigate();
  const [operacionId, setOperacionId] = useState(() => sessionStorage.getItem(OPERACION_PENDIENTE) ?? '');
  const [operacion, setOperacion] = useState<OperacionPrivacidad | null>(null);
  const [confirmarBaja, setConfirmarBaja] = useState(false);
  const [entendido, setEntendido] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (!operacionId) return;
    let activo = true;
    consultarOperacion(operacionId)
      .then(resultado => {
        if (!activo) return;
        setOperacion(resultado);
        if (resultado.estado === 'COMPLETADA' || resultado.estado === 'PENDIENTE_ARCHIVOS') {
          finalizarEnlace(resultado);
        }
      })
      .catch(e => {
        if (activo) setError(errorMessage(e));
      });
    return () => { activo = false; };
  // Only restore a saved request when the page first opens.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function finalizarEnlace(resultado: OperacionPrivacidad) {
    sessionStorage.removeItem(OPERACION_PENDIENTE);
    void navigate('/cuenta-eliminada', {
      replace: true,
      state: { estado: resultado.estado, retenciones: resultado.retenciones ?? [] },
    });
    clearSessionLocally();
  }

  async function descargar() {
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const datos = await exportarDatos(crearIdOperacion());
      descargarJson(datos);
      setSuccess('Tu archivo JSON se descargó. Guárdalo en un lugar seguro: contiene datos personales.');
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function enviarBaja() {
    const id = operacionId || crearIdOperacion();
    setOperacionId(id);
    sessionStorage.setItem(OPERACION_PENDIENTE, id);
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      const resultado = await solicitarBaja(id);
      setOperacion(resultado);
      setConfirmarBaja(false);
      setEntendido(false);
      if (resultado.estado === 'COMPLETADA' || resultado.estado === 'PENDIENTE_ARCHIVOS') {
        finalizarEnlace(resultado);
      } else {
        setSuccess('La solicitud está registrada. Puedes volver a intentar la coordinación desde esta pantalla.');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function revisarEstado() {
    if (!operacionId) return;
    setBusy(true);
    setError('');
    try {
      const resultado = await consultarOperacion(operacionId);
      setOperacion(resultado);
      if (resultado.estado === 'COMPLETADA' || resultado.estado === 'PENDIENTE_ARCHIVOS') {
        finalizarEnlace(resultado);
      } else {
        setSuccess('La baja todavía espera coordinación con el servicio de datos.');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="privacy-page">
      <header className="privacy-page__header">
        <p className="eyebrow">Tu cuenta</p>
        <h1>Privacidad y datos personales</h1>
        <p>Descarga una copia de tu información o solicita la baja de tu cuenta.</p>
      </header>

      <Feedback error={error} success={success} />

      <section className="card privacy-page__section" aria-labelledby="privacy-export-title">
        <div>
          <h2 id="privacy-export-title">Descargar mis datos</h2>
          <p>Recibirás un archivo JSON con la información de tu cuenta en Spring Boot y tus datos asociados en Django.</p>
        </div>
        <button type="button" disabled={busy} onClick={() => void descargar()}>
          {busy ? 'Procesando…' : 'Descargar archivo JSON'}
        </button>
      </section>

      <section className="card privacy-page__section privacy-page__section--danger" aria-labelledby="privacy-delete-title">
        <div>
          <h2 id="privacy-delete-title">Solicitar la baja de mi cuenta</h2>
          <p>Se anonimizarán tus datos personales y se eliminarán tus archivos privados. Esta acción no se puede deshacer.</p>
          <p className="muted">Algunos contratos y registros de auditoría pueden conservarse para mantener el historial. El resultado te indicará cuáles.</p>
        </div>
        {operacion?.estado === 'PENDIENTE_DJANGO' ? (
          <div className="privacy-page__pending" role="status">
            <p>La solicitud está pendiente de coordinación con el servicio de datos.</p>
            <div className="privacy-page__actions">
              <button type="button" disabled={busy} onClick={() => void enviarBaja()}>
                {busy ? 'Reintentando…' : 'Reintentar coordinación'}
              </button>
              <button type="button" className="secondary" disabled={busy} onClick={() => void revisarEstado()}>
                Consultar estado
              </button>
            </div>
          </div>
        ) : (
          <button type="button" className="danger" disabled={busy} onClick={() => setConfirmarBaja(true)}>
            Solicitar baja permanente
          </button>
        )}
        {operacion?.mensaje && <p className="privacy-page__status">{operacion.mensaje}</p>}
      </section>

      {confirmarBaja && (
        <div className="privacy-page__backdrop">
          <section className="card privacy-page__dialog" role="dialog" aria-modal="true"
            aria-labelledby="privacy-confirm-title" aria-describedby="privacy-confirm-description">
            <p className="eyebrow">Confirmación</p>
            <h2 id="privacy-confirm-title">¿Solicitar la baja definitiva?</h2>
            <p id="privacy-confirm-description">
              Se borrarán o anonimizarán los datos personales que Petly administra. Las referencias necesarias para conservar el historial de adopción pueden mantenerse.
            </p>
            <label className="privacy-page__check">
              <input type="checkbox" checked={entendido} onChange={event => setEntendido(event.target.checked)} />
              Entiendo que no podré recuperar mi cuenta después de completar la baja.
            </label>
            <div className="privacy-page__actions">
              <button type="button" className="secondary" disabled={busy} onClick={() => setConfirmarBaja(false)}>
                Cancelar
              </button>
              <button type="button" className="danger" disabled={busy || !entendido} onClick={() => void enviarBaja()}>
                {busy ? 'Enviando solicitud…' : 'Confirmar baja'}
              </button>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
