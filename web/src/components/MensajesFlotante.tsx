import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { django } from '../services/http';

export default function MensajesFlotante({ espacio }: { espacio: 'adoptante' | 'albergue' }) {
  const [total, setTotal] = useState(0);

  useEffect(() => {
    let activa = true;
    let pendiente = false;
    const actualizar = async () => {
      if (pendiente || document.hidden) return;
      pendiente = true;
      try {
        const datos = await django<{ no_leidos: number }>('/api/mensajeria/conversaciones/no-leidos/');
        if (activa) setTotal(datos.no_leidos);
      } catch {
        // Conservar el último conteo si hay una interrupción de red.
      } finally {
        pendiente = false;
      }
    };
    const consultar = () => { void actualizar(); };
    consultar();
    const timer = window.setInterval(consultar, 5000);
    document.addEventListener('visibilitychange', consultar);
    return () => {
      activa = false;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', consultar);
    };
  }, []);

  return (
    <Link className="mensajes-flotante" to={`/${espacio}/mensajes`}
      aria-label={total ? `Mensajes, ${total} sin leer` : 'Mensajes, sin mensajes pendientes'}>
      <span className="mensajes-flotante__icono">
        <img src="/images/mensajes-gatito.gif" alt="" />
      </span>
      {total > 0 && <span className="mensajes-flotante__contador" aria-hidden="true">{total > 99 ? '99+' : total}</span>}
    </Link>
  );
}
