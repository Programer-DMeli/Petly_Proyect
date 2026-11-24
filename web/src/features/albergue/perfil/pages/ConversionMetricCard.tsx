import { useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';

type Conversion = {
  solicitudes_recibidas: number;
  solicitudes_aprobadas: number;
  tasa_conversion: number | null;
};
type Resultado = { datos: Conversion } | { error: string };

const API = '/api/albergues/mi-perfil/metricas/conversion/';
const conteo = new Intl.NumberFormat('es-PE');
const porcentaje = new Intl.NumberFormat('es-PE', { style: 'percent', maximumFractionDigits: 2 });

export default function ConversionMetricCard() {
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void django<Conversion>(API, { signal: controller.signal }).then(datos => {
      if (!controller.signal.aborted) setResultado({ datos });
    }).catch(cause => {
      if (!controller.signal.aborted) setResultado({ error: errorMessage(cause) });
    });
    return () => controller.abort();
  }, [intento]);

  return <section className="card shelter-conversion-metric" aria-labelledby="shelter-conversion-title">
    <div>
      <h2 id="shelter-conversion-title">Conversión de solicitudes</h2>
      <p className="muted">Las aprobadas incluyen las solicitudes que llegaron a entrega.</p>
    </div>
    {resultado === null ? <p role="status">Cargando tasa de conversión…</p> : 'error' in resultado ? <div>
      <Feedback error={resultado.error} />
      <button type="button" className="secondary" onClick={() => {
        setResultado(null);
        setIntento(valor => valor + 1);
      }}>Reintentar conversión</button>
    </div> : <dl className="shelter-conversion-metric__figures" aria-live="polite">
      <div>
        <dt>Solicitudes recibidas</dt>
        <dd>{conteo.format(resultado.datos.solicitudes_recibidas)}</dd>
      </div>
      <div>
        <dt>Solicitudes aprobadas</dt>
        <dd>{conteo.format(resultado.datos.solicitudes_aprobadas)}</dd>
      </div>
      <div>
        <dt>Tasa de conversión</dt>
        <dd>{resultado.datos.tasa_conversion === null
          ? 'Sin datos'
          : porcentaje.format(resultado.datos.tasa_conversion / 100)}</dd>
      </div>
    </dl>}
  </section>;
}
