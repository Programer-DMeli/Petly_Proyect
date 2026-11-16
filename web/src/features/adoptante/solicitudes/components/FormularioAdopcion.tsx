import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import Feedback from '../../../../components/Feedback';
import { seccionesFormularioAdopcion } from '../../../../services/formularioAdopcion';
import type { RespuestasAdopcion } from '../../../../services/formularioAdopcion';

export default function FormularioAdopcion({
  detalle,
  enviando,
  error,
  onEnviar,
}: {
  detalle: string;
  enviando: boolean;
  error: string;
  onEnviar: (respuestas: RespuestasAdopcion) => void;
}) {
  const [respuestas, setRespuestas] = useState<RespuestasAdopcion>({});

  function actualizar(nombre: string, valor: string | boolean) {
    setRespuestas(actuales => ({ ...actuales, [nombre]: valor }));
  }

  function enviar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (enviando) return;
    const formulario = new FormData(event.currentTarget);
    const datos: RespuestasAdopcion = {};
    for (const { campos } of seccionesFormularioAdopcion) {
      for (const campo of campos) {
        const valor = formulario.get(campo.nombre);
        if (typeof valor === 'string') {
          datos[campo.nombre] = campo.tipo === 'booleano' ? valor === 'true' : valor.trim();
        }
      }
    }
    onEnviar(datos);
  }

  return (
    <form className="adoption-form" onSubmit={enviar}>
      {seccionesFormularioAdopcion.map(({ titulo, campos }) => (
        <fieldset className="adoption-section" key={titulo} disabled={enviando}>
          <legend>{titulo}</legend>
          <div className="adoption-fields">
            {campos
              .filter(campo => !campo.cuando || campo.cuando(respuestas))
              .map(campo => {
                const valor = String(respuestas[campo.nombre] ?? '');
                const control = { name: campo.nombre, value: valor, required: true };
                return (
                  <label
                    key={campo.nombre}
                    className={campo.tipo === 'area' ? 'adoption-field--wide' : undefined}
                  >
                    {campo.pregunta ?? campo.etiqueta}
                    {campo.tipo === 'booleano' || campo.tipo === 'opciones' ? (
                      <select
                        {...control}
                        onChange={event => {
                          const valor = event.currentTarget.value;
                          actualizar(
                            campo.nombre,
                            campo.tipo === 'booleano' && valor !== '' ? valor === 'true' : valor
                          );
                        }}
                      >
                        <option value="" disabled>
                          Selecciona una opción
                        </option>
                        {campo.tipo === 'booleano' ? (
                          <>
                            <option value="true">Sí</option>
                            <option value="false">No</option>
                          </>
                        ) : (
                          campo.opciones?.map(opcion => <option key={opcion}>{opcion}</option>)
                        )}
                      </select>
                    ) : campo.tipo === 'area' ? (
                      <textarea
                        {...control}
                        maxLength={2000}
                        onChange={event => {
                          const input = event.currentTarget;
                          input.setCustomValidity(
                            input.value && !input.value.trim() ? 'Escribe una respuesta.' : ''
                          );
                          actualizar(campo.nombre, input.value);
                        }}
                      />
                    ) : (
                      <input
                        {...control}
                        type={campo.tipo === 'telefono' ? 'tel' : 'text'}
                        inputMode={campo.tipo === 'dni' ? 'numeric' : undefined}
                        autoComplete={campo.tipo === 'dni' ? 'off' : undefined}
                        pattern={campo.tipo === 'dni' ? '[0-9]{8}' : '.*\\S.*'}
                        maxLength={campo.tipo === 'dni' ? 8 : campo.tipo === 'telefono' ? 32 : 250}
                        onChange={event => actualizar(campo.nombre, event.currentTarget.value)}
                      />
                    )}
                  </label>
                );
              })}
          </div>
        </fieldset>
      ))}
      <div>
        <Feedback error={error} />
        <div className="actions">
          <button disabled={enviando}>{enviando ? 'Enviando…' : 'Enviar solicitud'}</button>
          {!enviando && (
            <Link className="button secondary" to={detalle}>
              Cancelar
            </Link>
          )}
        </div>
      </div>
    </form>
  );
}
