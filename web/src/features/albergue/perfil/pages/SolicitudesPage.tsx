import { useCallback, useEffect, useState } from 'react';
import FotosHogar from '../../../../components/FotosHogar';
import Feedback from '../../../../components/Feedback';
import { django, json, errorMessage } from '../../../../services/http';

type Solicitud = {
  id: number;
  respuestas_cuestionario?: Record<string, unknown>;
  datos_compartidos?: boolean;
  estado: 'PENDIENTE' | 'OBSERVADA' | 'APROBADA' | 'RECHAZADA' | 'CANCELADA' | 'ENTREGADA';
  fecha_solicitud: string;
  mensaje_postulante: string;
  observaciones_revision: string;
  postulante_nombre: string;
  mascota_nombre: string;
};
function respuesta(value: unknown): string {
  if (typeof value === 'boolean') return value ? 'Sí' : 'No';
  if (typeof value === 'string' || typeof value === 'number') return `${value}`;
  return 'Sin responder';
}
type Page<T> = { results: T[] } | T[];

export default function SolicitudesPage() {
  const [rows, setRows] = useState<Solicitud[]>([]);
  const [reasons, setReasons] = useState<Record<number, string>>({});
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [busy, setBusy] = useState<number | null>(null);
  const load = useCallback(async () => {
    try {
      const response = await django<Page<Solicitud>>('/api/acceso/solicitudes/');
      setRows(Array.isArray(response) ? response : response.results);
      setError('');
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);
  useEffect(() => {
    void load();
  }, [load]);

  async function decidir(id: number, estado: 'OBSERVADA' | 'APROBADA' | 'RECHAZADA') {
    const motivo = reasons[id]?.trim() ?? '';
    if ((estado === 'OBSERVADA' || estado === 'RECHAZADA') && !motivo) {
      setError('Escribe el motivo antes de observar o rechazar la solicitud.');
      return;
    }
    setBusy(id);
    setError('');
    setSuccess('');
    try {
      await django(`/api/acceso/solicitudes/${id}/cambiar-estado/`, {
        method: 'POST',
        body: json({ estado, observaciones_revision: motivo }),
      });
      setSuccess('Decisión guardada.');
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="card">
      <h1>Solicitudes de adopción</h1>
      <Feedback error={error} success={success} />
      <button onClick={() => void load()}>Actualizar</button>
      {rows.length === 0 ? (
        <p>No hay solicitudes para revisar.</p>
      ) : (
        rows.map(row => (
          <article className="card" key={row.id}>
            <h2>
              {row.mascota_nombre} · {row.postulante_nombre}
            </h2>
            <p>
              Estado: {row.estado} · {new Date(row.fecha_solicitud).toLocaleDateString()}
            </p>
            {row.mensaje_postulante && <p>Mensaje: {row.mensaje_postulante}</p>}
            {row.datos_compartidos === false && (
              <p>El perfil y formulario privado requieren autorización vigente.</p>
            )}
            {row.respuestas_cuestionario && Object.keys(row.respuestas_cuestionario).length > 0 && (
              <details>
                <summary>Formulario del adoptante</summary>
                <dl>
                  {Object.entries({
                    nombreCompleto: 'Nombre declarado',
                    dni: 'DNI declarado (sin verificación de identidad)',
                    direccionHogar: 'Dirección del hogar',
                    telefonoContacto: 'Teléfono',
                    tipoPropiedadVivienda: 'Propiedad de vivienda',
                    permisoPropietarioDetalle: 'Permiso del propietario',
                    espaciosDisponiblesMascota: 'Espacios disponibles',
                    tieneAlergias: 'Alergias',
                    alergiasDetalle: 'Detalle de alergias',
                    vivesConPersonasEspeciales: 'Personas que requieren cuidados',
                    personasEspecialesDetalle: 'Detalle de convivencia',
                    motivoAdopcion: 'Motivo de adopción',
                    haCuidadoMascotasAntes: 'Experiencia previa',
                    experienciaPreviaDetalle: 'Detalle de experiencia',
                    aceptaEsterilizacion: 'Acepta esterilización',
                    aceptaSeguimiento: 'Acepta seguimiento',
                    puedeCubrirGastosVeterinarios: 'Puede cubrir gastos veterinarios',
                  }).map(([key, label]) => (
                    <div key={key}>
                      <dt>{label}</dt>
                      <dd>{respuesta(row.respuestas_cuestionario?.[key])}</dd>
                    </div>
                  ))}
                </dl>
              </details>
            )}
            <FotosHogar solicitudId={row.id} />
            {row.observaciones_revision && <p>Observación: {row.observaciones_revision}</p>}
            {['PENDIENTE', 'OBSERVADA'].includes(row.estado) && (
              <>
                <label>
                  Motivo de observación o rechazo
                  <textarea
                    value={reasons[row.id] ?? ''}
                    onChange={e => setReasons(v => ({ ...v, [row.id]: e.target.value }))}
                  />
                </label>
                <div className="grid">
                  <button
                    disabled={busy !== null}
                    onClick={() => void decidir(row.id, 'OBSERVADA')}
                  >
                    Observar
                  </button>
                  <button disabled={busy !== null} onClick={() => void decidir(row.id, 'APROBADA')}>
                    Aprobar
                  </button>
                  <button
                    disabled={busy !== null}
                    onClick={() => void decidir(row.id, 'RECHAZADA')}
                  >
                    Rechazar
                  </button>
                </div>
              </>
            )}
          </article>
        ))
      )}
    </section>
  );
}
