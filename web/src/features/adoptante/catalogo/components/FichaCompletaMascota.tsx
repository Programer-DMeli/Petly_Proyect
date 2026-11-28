import { useId } from 'react';
import { Link } from 'react-router-dom';
import ConsejosCompatibilidad from '../../../../components/ConsejosCompatibilidad';
import { formatAge } from '../../../../services/formatAge';
import { mediaUrl } from '../../../../services/http';
import type { Pet } from '../../../../services/types';
import './ficha-completa-mascota.css';

export default function FichaCompletaMascota({
  mascota, mostrarFoto = true,
}: {
  mascota: Pet;
  mostrarFoto?: boolean;
}) {
  const compatibilidadTitulo = useId();
  const informacionTitulo = useId();
  const albergueTitulo = useId();
  const compatibilidad = mascota.compatibilidad;
  const disponible = mascota.estado.trim().toLowerCase() === 'disponible';

  return (
    <div className="pet-full-profile">
      {mostrarFoto && mascota.fotografiaUrl && (
        <img className="detail-photo" src={mediaUrl(mascota.fotografiaUrl)} alt={mascota.nombre} />
      )}
      <section className="pet-full-profile__block" aria-labelledby={informacionTitulo}>
        <header className="pet-full-profile__heading">
          <h2 id={informacionTitulo}>Datos de {mascota.nombre}</h2>
          <span className="badge">{mascota.estado}</span>
        </header>
        <p>{mascota.especie} · {mascota.tamano} · {formatAge(mascota.edadMeses)}</p>
        <dl className="pet-full-profile__facts">
          {mascota.raza && <div><dt>Raza</dt><dd>{mascota.raza}</dd></div>}
          {mascota.sexo && <div><dt>Sexo</dt><dd>{mascota.sexo}</dd></div>}
          <div><dt>Temperamento</dt><dd>{mascota.temperamento}</dd></div>
          <div><dt>Energía</dt><dd>{mascota.energia}</dd></div>
        </dl>
        {mascota.descripcion && (
          <div className="pet-full-profile__description">
            <h3>Descripción</h3>
            <p>{mascota.descripcion}</p>
          </div>
        )}
      </section>
      {compatibilidad && (
        <section className="pet-full-profile__block" aria-labelledby={compatibilidadTitulo}>
          <h2 id={compatibilidadTitulo}>¿Cómo se calcula tu compatibilidad?</h2>
          <dl className="pet-full-profile__metrics">
            <div>
              <dt>Puntaje</dt>
              <dd>{compatibilidad.porcentaje === null ? 'Información insuficiente' : `${compatibilidad.porcentaje}%`}</dd>
            </div>
            <div>
              <dt>Cobertura de datos</dt>
              <dd>{compatibilidad.cobertura}%</dd>
            </div>
          </dl>
          {compatibilidad.mensaje && <p>{compatibilidad.mensaje}</p>}
          {compatibilidad.criterios?.length > 0 && (
            <ul className="pet-full-profile__criteria" aria-label="Criterios de compatibilidad">
              {compatibilidad.criterios.map(criterion => (
                <li key={criterion.id}>
                  <div className="pet-full-profile__heading">
                    <strong>{criterion.nombre}</strong>
                    <span>Peso {criterion.peso}%</span>
                  </div>
                  <p className="pet-full-profile__points">
                    {criterion.aporte === null
                      ? 'Aporte pendiente por falta de datos'
                      : `Aporta ${criterion.aporte.toFixed(2)} puntos`}
                  </p>
                  <p>{criterion.explicacion}</p>
                </li>
              ))}
            </ul>
          )}
          {compatibilidad.versionReglas && <p className="pet-full-profile__note">Reglas: {compatibilidad.versionReglas}</p>}
          <p className="pet-full-profile__note">
            El total ponderado se redondea al entero más cercano. Es una orientación; el
            albergue decide cada adopción.
          </p>
        </section>
      )}
      <div className="pet-full-profile__block pet-full-profile__assistant">
        <ConsejosCompatibilidad key={mascota.id} id={mascota.id} />
      </div>
      <section className="pet-full-profile__block" aria-labelledby={albergueTitulo}>
        <h2 id={albergueTitulo}>{mascota.albergue}</h2>
        <p>{mascota.ubicacion}</p>
        <details className="pet-full-profile__action">
          <summary className="button">
            <span>Elegir una acción</span>
            <span className="pet-full-profile__chevron" aria-hidden="true" />
          </summary>
          <div className="pet-full-profile__action-panel">
            {disponible && (
              <Link className="button" to={`/adoptante/catalogo/${mascota.id}/solicitud`}>
                Llenar formulario de adopción
              </Link>
            )}
            <Link className="button secondary" to={`/adoptante/mensajes?albergue=${mascota.albergueId}`}>
              Escribir al albergue
            </Link>
            <Link className="button secondary" to="/adoptante/autorizaciones">
              Administrar autorizaciones de mi perfil
            </Link>
          </div>
        </details>
        {!disponible && <p role="status">Esta mascota no está disponible para solicitudes.</p>}
      </section>
    </div>
  );
}
