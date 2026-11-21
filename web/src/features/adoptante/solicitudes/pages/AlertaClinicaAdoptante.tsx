import { useEffect, useState } from 'react';
import { django } from '../../../../services/http';

type Consulta = { alerta: string } | { error: true };

export default function AlertaClinicaAdoptante({ solicitudId }: { solicitudId: number }) {
  const [consulta, setConsulta] = useState<Consulta | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void django<{ alerta_clinica: string }>(`/api/acceso/solicitudes/${solicitudId}/alerta-clinica/`, {
      signal: controller.signal, cache: 'no-store',
    }).then(({ alerta_clinica }) => {
      if (!controller.signal.aborted) setConsulta({ alerta: alerta_clinica });
    }).catch(() => {
      if (!controller.signal.aborted) setConsulta({ error: true });
    });
    return () => controller.abort();
  }, [solicitudId]);

  if (!consulta) return <p className="muted" role="status">Consultando alerta clínica…</p>;
  if ('error' in consulta) return <p className="muted">No se pudo consultar la alerta clínica.</p>;
  if (!consulta.alerta) return <p className="muted">Sin alerta clínica registrada.</p>;

  return (
    <section className="alert alert--warning alerta-clinica" aria-label="Alerta clínica">
      <h3>Alerta clínica</h3>
      <p>{consulta.alerta}</p>
    </section>
  );
}
