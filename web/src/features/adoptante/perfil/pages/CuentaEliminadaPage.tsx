import { Link, useLocation } from 'react-router-dom';
import './privacidad.css';

type EstadoFinal = 'PENDIENTE_ARCHIVOS' | 'COMPLETADA';
type ResultadoState = { estado?: EstadoFinal; retenciones?: string[] };

export default function CuentaEliminadaPage() {
  const resultado = (useLocation().state ?? {}) as ResultadoState;
  const archivosPendientes = resultado.estado === 'PENDIENTE_ARCHIVOS';
  const operacionConfirmada = resultado.estado === 'COMPLETADA' || archivosPendientes;

  return (
    <main className="privacy-page privacy-page--public">
      <section className="card privacy-page__result" aria-labelledby="privacy-result-title">
        <p className="eyebrow">Privacidad</p>
        <h1 id="privacy-result-title">
          {!operacionConfirmada ? 'Estado de privacidad' : archivosPendientes ? 'La baja de tu cuenta fue procesada' : 'Tu cuenta fue eliminada'}
        </h1>
        <p>
          {!operacionConfirmada
            ? 'No hay un resultado de baja asociado a esta página.'
            : archivosPendientes
              ? 'Tu cuenta ya no está activa. Petly terminará de eliminar los archivos privados pendientes.'
              : 'La solicitud de baja terminó y tu sesión se cerró.'}
        </p>
        {resultado.retenciones && resultado.retenciones.length > 0 && (
          <div className="privacy-page__retentions">
            <h2>Datos que se conservan</h2>
            <ul>{resultado.retenciones.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul>
          </div>
        )}
        <Link className="button" to="/">Volver al inicio</Link>
      </section>
    </main>
  );
}
