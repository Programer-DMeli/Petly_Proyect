import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';

type Permanencia = {
  dias_promedio: number | null;
  mascotas_entregadas: number;
};

const API = '/api/albergues/mi-perfil/metricas/permanencia/';

export default function PermanenciaMetricCard() {
  const [metricas, setMetricas] = useState<Permanencia | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const resultado = await django<Permanencia>(API, { signal });
      if (!signal?.aborted) setMetricas(resultado);
    } catch (cause) {
      if (!signal?.aborted) setError(errorMessage(cause));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void cargar(controller.signal);
    return () => controller.abort();
  }, [cargar]);

  const promedio = metricas?.dias_promedio;
  const cantidad = metricas?.mascotas_entregadas ?? 0;

  return <section className="card shelter-stay-metric" aria-labelledby="shelter-stay-title">
    <div>
      <p className="eyebrow">Impacto de adopciones</p>
      <h2 id="shelter-stay-title">Permanencia promedio</h2>
      <p className="muted">Días entre el ingreso de una mascota y la entrega de su adopción.</p>
    </div>
    {loading ? <p role="status">Cargando métrica…</p> : error ? <>
      <Feedback error={error} />
      <button type="button" className="secondary" onClick={() => void cargar()}>Reintentar</button>
    </> : promedio === null || promedio === undefined ? <p role="status">
      Aún no hay adopciones entregadas para calcular esta métrica.
    </p> : <div aria-live="polite">
      <strong className="shelter-stay-metric__value">
        {new Intl.NumberFormat('es-PE', { maximumFractionDigits: 1 }).format(promedio)} días
      </strong>
      <p className="muted">Promedio de {cantidad} {cantidad === 1 ? 'mascota entregada' : 'mascotas entregadas'}.</p>
    </div>}
  </section>;
}
