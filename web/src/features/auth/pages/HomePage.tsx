import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getHealthUsuario } from '../../../services/usuarioApi'
import { getHealthAdministracion } from '../../../services/administracionApi'

type Estado = 'comprobando' | 'disponible' | 'no-disponible'

/**
 * Pantalla inicial de Petly web: presentación y comprobación de los
 * dos backends mediante sus endpoints de salud.
 */
export default function HomePage() {
  const [estadoUsuario, setEstadoUsuario] = useState<Estado>('comprobando')
  const [estadoAdmin, setEstadoAdmin] = useState<Estado>('comprobando')

  useEffect(() => {
    let activo = true

    getHealthUsuario()
      .then(() => activo && setEstadoUsuario('disponible'))
      .catch(() => activo && setEstadoUsuario('no-disponible'))

    getHealthAdministracion()
      .then(() => activo && setEstadoAdmin('disponible'))
      .catch(() => activo && setEstadoAdmin('no-disponible'))

    return () => {
      activo = false
    }
  }, [])

  const etiqueta = (estado: Estado) =>
    estado === 'comprobando' ? 'comprobando…' : estado === 'disponible' ? 'disponible' : 'sin conexión'

  return (
    <div>
      <section className="hero">
        <h1>Petly</h1>
        <p>
          Plataforma de adopción responsable de mascotas: conecta adoptantes con albergues,
          orienta la búsqueda mediante compatibilidad con el estilo de vida y organiza
          publicaciones, solicitudes y seguimiento.
        </p>
      </section>

      <div className="grid">
        <section className="card">
          <h2>Acceso</h2>
          <p className="muted">Entra con tu cuenta o recupera tu contraseña.</p>
          <p>
            <Link to="/acceso">Ir al acceso</Link>
          </p>
          <p>
            <Link to="/recuperacion">Recuperar contraseña</Link>
          </p>
        </section>

        <section className="card">
          <h2>Áreas</h2>
          <p className="muted">
            El portal del adoptante en React está previsto para el Sprint 2; la experiencia
            completa del adoptante se desarrolla en Android.
          </p>
          <p>
            <Link to="/albergue">Panel del albergue</Link>
          </p>
          <p>
            <Link to="/administracion/albergues">Administración</Link>
          </p>
        </section>

        <section className="card">
          <h2>Estado de los servicios</h2>
          <ul className="status-list">
            <li>
              <span>Spring Boot (:8080)</span>
              <span className="muted">{etiqueta(estadoUsuario)}</span>
            </li>
            <li>
              <span>Django (:8000)</span>
              <span className="muted">{etiqueta(estadoAdmin)}</span>
            </li>
          </ul>
        </section>
      </div>
    </div>
  )
}
