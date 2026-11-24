import { useEffect, useState } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage } from '../../../../services/http';

type Agrupacion = 'especie' | 'raza';
type Conteo = {
  mascotas_ingresadas: number;
  mascotas_entregadas: number;
  tasa_adopcion: number | null;
  dias_promedio: number | null;
};
type Grupo = Conteo & { especie: string; nombre_especie: string; raza: string | null };
type Ranking = { hasta: string; totales: Conteo; grupos: Grupo[] };
type Resultado = { consulta: string; datos: Ranking } | { consulta: string; error: string };

const numeros = new Intl.NumberFormat('es-PE');
const tasas = new Intl.NumberFormat('es-PE', { style: 'percent', maximumFractionDigits: 2 });
const fechas = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' });

function porcentaje(valor: number | null) {
  return valor === null ? 'Sin datos' : tasas.format(valor / 100);
}

function Cifras({ conteo }: { conteo: Conteo }) {
  return <>
    <td>{numeros.format(conteo.mascotas_ingresadas)}</td>
    <td>{numeros.format(conteo.mascotas_entregadas)}</td>
    <td>{porcentaje(conteo.tasa_adopcion)}</td>
    <td>{conteo.dias_promedio === null ? 'Sin datos' : numeros.format(conteo.dias_promedio)}</td>
  </>;
}

export default function RankingAdopciones({ desde, hasta }: { desde: string; hasta: string }) {
  const [agrupacion, setAgrupacion] = useState<Agrupacion>('especie');
  const [intento, setIntento] = useState(0);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const parametros = new URLSearchParams({ agrupacion });
  if (desde) parametros.set('desde', desde);
  if (hasta) parametros.set('hasta', hasta);
  const url = `/api/acceso/analitica/adopciones/ranking/?${parametros}`;
  const consulta = `${url}#${intento}`;

  useEffect(() => {
    const controller = new AbortController();
    void django<Ranking>(url, { signal: controller.signal })
      .then(datos => {
        if (!controller.signal.aborted) setResultado({ consulta, datos });
      })
      .catch(cause => {
        if (!controller.signal.aborted) setResultado({ consulta, error: errorMessage(cause) });
      });
    return () => controller.abort();
  }, [consulta, url]);

  const actual = resultado?.consulta === consulta ? resultado : null;
  const porRaza = agrupacion === 'raza';

  return <section className="card adoption-analytics__chart adoption-ranking" aria-labelledby="ranking-adopciones">
    <header className="adoption-ranking__header">
      <h2 id="ranking-adopciones">Ranking de adopción</h2>
      <label htmlFor="ranking-agrupacion">
        Agrupar por
        <select id="ranking-agrupacion" value={agrupacion}
          onChange={event => setAgrupacion(event.target.value === 'raza' ? 'raza' : 'especie')}>
          <option value="especie">Por especie</option>
          <option value="raza">Por raza</option>
        </select>
      </label>
    </header>
    {actual === null ? <p role="status">Cargando ranking de adopción…</p> : 'error' in actual ? <div>
      <Feedback error={actual.error} />
      <button type="button" className="secondary" onClick={() => setIntento(valor => valor + 1)}>Reintentar ranking</button>
    </div> : actual.datos.grupos.length === 0 ? (
      <p className="adoption-analytics__empty" role="status">Sin datos. No hubo ingresos en el periodo seleccionado.</p>
    ) : <>
      <p className="muted">De las mascotas ingresadas en el periodo, cuántas se entregaron hasta el {fechas.format(new Date(`${actual.datos.hasta}T12:00:00`))}. Ordenado de menor a mayor tasa.</p>
      <div className="adoption-ranking__scroll" role="region" aria-label="Tasas y permanencia por grupo" tabIndex={0}>
        <table>
          <thead><tr>
            <th scope="col">Especie</th>
            {porRaza && <th scope="col">Raza</th>}
            <th scope="col">Ingresadas</th>
            <th scope="col">Entregadas</th>
            <th scope="col" aria-sort="ascending">Tasa de adopción</th>
            <th scope="col">Permanencia (días)</th>
          </tr></thead>
          <tbody>
            {actual.datos.grupos.map(grupo => <tr key={JSON.stringify([grupo.especie, grupo.raza])}>
              {porRaza ? <>
                <td>{grupo.nombre_especie}</td>
                <th scope="row">{grupo.raza ?? 'Sin identificar'}</th>
              </> : <th scope="row">{grupo.nombre_especie}</th>}
              <Cifras conteo={grupo} />
            </tr>)}
          </tbody>
          <tfoot><tr>
            <th scope="row" colSpan={porRaza ? 2 : 1}>Total</th>
            <Cifras conteo={actual.datos.totales} />
          </tr></tfoot>
        </table>
      </div>
    </>}
  </section>;
}
