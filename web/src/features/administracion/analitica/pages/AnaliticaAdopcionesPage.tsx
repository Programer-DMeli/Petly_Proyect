import { useCallback, useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';

type AdopcionesPorMes = {
  anio: number;
  mes: number;
  total: number;
};
type AdopcionesPorEspecie = {
  especie: string;
  nombre: string;
  total: number;
};
type AnaliticaAdopciones = {
  total_adopciones: number;
  por_mes: AdopcionesPorMes[];
  por_especie: AdopcionesPorEspecie[];
};

const API = '/api/acceso/analitica/adopciones/';
const MESES = new Intl.DateTimeFormat('es-PE', { month: 'short' });

function nombreMes(anio: number, mes: number) {
  const abreviado = MESES.format(new Date(anio, mes - 1, 1));
  return abreviado.replace('.', '') + ' ' + anio;
}

function BarraHorizontal({
  label,
  value,
  maximum,
}: {
  label: string;
  value: number;
  maximum: number;
}) {
  const porcentaje = maximum > 0 ? (value / maximum) * 100 : 0;
  return <div className="adoption-bar-chart__row" role="listitem" aria-label={label + ': ' + value + ' adopciones'}>
    <span className="adoption-bar-chart__label">{label}</span>
    <div className="adoption-bar-chart__track" aria-hidden="true">
      <span className="adoption-bar-chart__bar" style={{ width: porcentaje + '%' }} />
    </div>
    <strong className="adoption-bar-chart__value">{value}</strong>
  </div>;
}

function GraficoMensual({ datos }: { datos: AdopcionesPorMes[] }) {
  const maximo = Math.max(0, ...datos.map(item => item.total));
  if (datos.length === 0) {
    return <p className="adoption-analytics__empty">Aún no hay adopciones entregadas para mostrar.</p>;
  }

  return <div
    className="adoption-bar-chart"
    role="list"
    aria-label="Adopciones entregadas agrupadas por mes y año">
    {datos.map(item => <BarraHorizontal
      key={item.anio + '-' + item.mes}
      label={nombreMes(item.anio, item.mes)}
      value={item.total}
      maximum={maximo}
    />)}
  </div>;
}

function GraficoEspecies({ datos }: { datos: AdopcionesPorEspecie[] }) {
  const maximo = Math.max(0, ...datos.map(item => item.total));
  if (datos.length === 0) {
    return <p className="adoption-analytics__empty">No hay categorías de especies disponibles.</p>;
  }

  return <div
    className="adoption-bar-chart"
    role="list"
    aria-label="Adopciones entregadas agrupadas por especie">
    {datos.map(item => <BarraHorizontal
      key={item.especie}
      label={item.nombre}
      value={item.total}
      maximum={maximo}
    />)}
  </div>;
}

export default function AnaliticaAdopcionesPage() {
  const [datos, setDatos] = useState<AnaliticaAdopciones | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const resultado = await django<AnaliticaAdopciones>(API, { signal });
      if (!signal?.aborted) setDatos(resultado);
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

  return <main className="adoption-analytics">
    <header className="adoption-analytics__heading">
      <p className="eyebrow">Impacto de Petly</p>
      <h1>Adopciones</h1>
      <p className="muted">Consulta las mascotas entregadas, agrupadas por fecha y especie.</p>
    </header>
    <Feedback error={error} />
    {error && <button type="button" className="secondary" onClick={() => void cargar()}>
      Reintentar
    </button>}
    {loading ? <p role="status">Cargando estadísticas de adopción…</p>
      : datos && <>
        <section className="adoption-analytics__summary" aria-label="Resumen de adopciones">
          <article className="card adoption-analytics__total">
            <span>Total de mascotas entregadas</span>
            <strong>{datos.total_adopciones.toLocaleString('es-PE')}</strong>
          </article>
        </section>
        <div className="adoption-analytics__charts">
          <section className="card adoption-analytics__chart" aria-labelledby="adopciones-mes-titulo">
            <div>
              <p className="eyebrow">Evolución</p>
              <h2 id="adopciones-mes-titulo">Adopciones por mes</h2>
              <p className="muted">Cada barra representa mascotas con entrega registrada en ese mes.</p>
            </div>
            <GraficoMensual datos={datos.por_mes} />
          </section>
          <section className="card adoption-analytics__chart" aria-labelledby="adopciones-especie-titulo">
            <div>
              <p className="eyebrow">Especies</p>
              <h2 id="adopciones-especie-titulo">Adopciones por especie</h2>
              <p className="muted">El total incluye solo solicitudes en estado entregado.</p>
            </div>
            <GraficoEspecies datos={datos.por_especie} />
          </section>
        </div>
      </>}
  </main>;
}
