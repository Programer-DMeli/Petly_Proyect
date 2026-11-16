import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { errorMessage, json, mediaUrl, spring } from '../../../../services/http';
import type { RespuestasAdopcion } from '../../../../services/formularioAdopcion';
import type {
  CrearSolicitudAdopcion,
  Pet,
  Profile,
  SolicitudAdopcion,
} from '../../../../services/types';
import FormularioAdopcion from '../components/FormularioAdopcion';

type AccesoFormulario =
  | { pet: Pet; disponible: false }
  | { pet: Pet; disponible: true; perfilFinalizado: boolean; autorizacionActiva: boolean };

export default function FormularioAdopcionPage() {
  const { id } = useParams();
  return <SolicitudParaMascota key={id} id={Number(id)} />;
}

function SolicitudParaMascota({ id }: { id: number }) {
  const navigate = useNavigate();
  const detalle = `/adoptante/catalogo/${id}`;
  const idValido = Number.isSafeInteger(id) && id > 0;
  const [acceso, setAcceso] = useState<AccesoFormulario | null>(null);
  const [cargando, setCargando] = useState(idValido);
  const [intento, setIntento] = useState(0);
  const [errorCarga, setErrorCarga] = useState('');
  const [errorEnvio, setErrorEnvio] = useState('');
  const [enviando, setEnviando] = useState(false);
  const envioActivo = useRef(false);

  useEffect(() => {
    if (!idValido) return;
    const controller = new AbortController();
    const options = { signal: controller.signal };
    async function cargar() {
      setCargando(true);
      setErrorCarga('');
      try {
        const pet = await spring<Pet>(`/api/catalogo/${id}`, options);
        if (pet.estado.trim().toLowerCase() !== 'disponible') {
          if (!controller.signal.aborted) setAcceso({ pet, disponible: false });
          return;
        }
        const [perfil, autorizaciones] = await Promise.all([
          spring<Profile>('/api/perfil', options),
          spring<{ albergueId: number; estado: string }[]>('/api/autorizaciones', options),
        ]);
        if (!controller.signal.aborted)
          setAcceso({
            pet,
            disponible: true,
            perfilFinalizado: perfil.finalizado,
            autorizacionActiva: autorizaciones.some(
              item => item.albergueId === pet.albergueId && item.estado === 'ACTIVA'
            ),
          });
      } catch (error) {
        if (!controller.signal.aborted) setErrorCarga(errorMessage(error));
      } finally {
        if (!controller.signal.aborted) setCargando(false);
      }
    }
    void cargar();
    return () => controller.abort();
  }, [id, idValido, intento]);

  const puedeEnviar = acceso?.disponible && acceso.perfilFinalizado && acceso.autorizacionActiva;

  async function enviar(respuestas: RespuestasAdopcion) {
    if (!puedeEnviar || envioActivo.current) return;
    envioActivo.current = true;
    setEnviando(true);
    setErrorEnvio('');
    const payload: CrearSolicitudAdopcion = {
      mascotaId: id,
      respuestasCuestionario: respuestas,
    };
    try {
      const solicitud = await spring<SolicitudAdopcion>('/api/solicitudes', {
        method: 'POST',
        body: json(payload),
      });
      void navigate(`/adoptante/solicitudes/${solicitud.id}`, {
        replace: true,
        state: { recienEnviada: true },
      });
    } catch (error) {
      setErrorEnvio(errorMessage(error));
    } finally {
      envioActivo.current = false;
      setEnviando(false);
    }
  }

  return (
    <section className="card adoption-application">
      <Link to={idValido ? detalle : '/adoptante/catalogo'}>
        {idValido ? 'Volver al detalle' : 'Volver al catálogo'}
      </Link>
      <h1>Formulario de adopción</h1>
      {!idValido && <Feedback error="La mascota indicada no es válida." />}
      {cargando && <p role="status">Cargando formulario…</p>}
      {errorCarga && (
        <>
          <Feedback error={errorCarga} />
          <button
            type="button"
            className="secondary"
            onClick={() => setIntento(actual => actual + 1)}
          >
            Reintentar
          </button>
        </>
      )}
      {acceso && (
        <>
          <div className="adoption-pet">
            {acceso.pet.fotografiaUrl && <img src={mediaUrl(acceso.pet.fotografiaUrl)} alt="" />}
            <div>
              <h2>{acceso.pet.nombre}</h2>
              <p>{acceso.pet.albergue}</p>
            </div>
          </div>
          {!acceso.disponible && (
            <p role="status">Esta mascota no está disponible para solicitudes.</p>
          )}
          {acceso.disponible && !acceso.perfilFinalizado && (
            <p>
              Finaliza tu cuestionario de perfil antes de enviar la solicitud.{' '}
              <Link to="/adoptante/cuestionario">Completar perfil</Link>
            </p>
          )}
          {acceso.disponible && !acceso.autorizacionActiva && (
            <p>
              Para compartir tus respuestas, autoriza a este albergue.{' '}
              <Link to="/adoptante/autorizaciones">Gestionar autorización</Link>
            </p>
          )}
          {puedeEnviar && (
            <>
              <p className="muted">
                Tus respuestas se compartirán con {acceso.pet.albergue} mientras tu autorización
                esté vigente.
              </p>
              <FormularioAdopcion
                detalle={detalle}
                enviando={enviando}
                error={errorEnvio}
                onEnviar={respuestas => void enviar(respuestas)}
              />
            </>
          )}
        </>
      )}
    </section>
  );
}
