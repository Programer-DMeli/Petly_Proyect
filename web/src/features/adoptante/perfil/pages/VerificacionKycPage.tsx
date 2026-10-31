import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage, spring } from '../../../../services/http';

interface VerificacionKyc {
  id: number;
  dni: string;
  nombres: string;
  apellidos: string;
  estado: string;
  coincide: boolean | null;
  proveedor: string;
  referencia_reniec: string;
  fecha_consulta: string | null;
  ultimo_intento_en: string | null;
  error_servicio: string;
  motivo_revision: string;
  fecha_revision: string | null;
}

interface Page<T> { results: T[] }
interface ReniecResult { estado: string; coincide: boolean | null; fechaConsulta: string; referencia: string }
interface ApiPeruDniResult { dni: string; nombres: string; apellidos: string; proveedor: string }

function firstRecord(payload: VerificacionKyc[] | Page<VerificacionKyc>) {
  return Array.isArray(payload) ? payload[0] : payload.results[0];
}

export default function VerificacionKycPage() {
  const [record, setRecord] = useState<VerificacionKyc | null>(null);
  const [dni, setDni] = useState('');
  const [nombres, setNombres] = useState('');
  const [apellidos, setApellidos] = useState('');
  const [front, setFront] = useState<File | null>(null);
  const [back, setBack] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [dniLookup, setDniLookup] = useState('');
  const [lookupAttempt, setLookupAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const lastLookupDni = useRef('');

  const load = useCallback(async () => {
    setError('');
    try {
      const data = await django<VerificacionKyc[] | Page<VerificacionKyc>>('/api/acceso/verificaciones-kyc/');
      const item = firstRecord(data) ?? null;
      setRecord(item);
      if (item) {
        lastLookupDni.current = item.dni;
        setDni(item.dni);
        setNombres(item.nombres);
        setApellidos(item.apellidos);
      }
    } catch (e) { setError(errorMessage(e)); }
  }, []);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    const cleanDni = dni.trim();
    if (!/^[0-9]{8}$/.test(cleanDni)) {
      setDniLookup('');
      return;
    }
    if ((lookupAttempt === 0 && record?.dni === cleanDni && nombres && apellidos) || lastLookupDni.current === cleanDni) return;

    let active = true;
    const timer = window.setTimeout(() => { void (async () => {
      setDniLookup('Buscando datos del DNI…');
      try {
        const result = await spring<ApiPeruDniResult>('/api/kyc/dni', {
          method: 'POST', body: JSON.stringify({ dni: cleanDni }),
        });
        if (!active) return;
        lastLookupDni.current = cleanDni;
        setNombres(result.nombres);
        setApellidos(result.apellidos);
        setDniLookup(`Datos completados desde ${result.proveedor}. Verifícalos antes de guardar.`);
      } catch (e) {
        if (!active) return;
        lastLookupDni.current = cleanDni;
        setDniLookup(errorMessage(e));
      }
    })(); }, 500);
    return () => { active = false; window.clearTimeout(timer); };
  }, [dni, record?.dni, nombres, apellidos, lookupAttempt]);

  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(''); setSuccess('');
    try {
      const body = new FormData();
      body.set('dni', dni.trim());
      body.set('nombres', nombres.trim());
      body.set('apellidos', apellidos.trim());
      if (front) body.set('documento_anverso', front);
      if (back) body.set('documento_reverso', back);
      const saved = await django<VerificacionKyc>('/api/acceso/verificaciones-kyc/', { method: 'POST', body });
      setRecord(saved);
      setFront(null);
      setBack(null);
      setSuccess('Documentos recibidos en almacenamiento privado. Puedes iniciar la consulta cuando estés listo.');
    } catch (e) { setError(errorMessage(e)); }
    finally { setBusy(false); }
  }

  async function consult() {
    if (!record) return;
    setBusy(true); setError(''); setSuccess('');
    try {
      const result = await spring<ReniecResult>(`/api/kyc/verificaciones/${record.id}/consultar`, { method: 'POST' });
      setRecord({ ...record, estado: result.estado, coincide: result.coincide, fecha_consulta: result.fechaConsulta, referencia_reniec: result.referencia, error_servicio: '' });
      setSuccess(result.coincide ? 'Los nombres y apellidos declarados coinciden con los datos consultados en API Perú.' : 'La identidad quedó pendiente de revisión manual.');
    } catch (e) { setError(errorMessage(e)); await load(); }
    finally { setBusy(false); }
  }

  const fieldErrors = [front, back].some(file => file && (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)));
  const pendingReview = record?.estado === 'PENDIENTE_REVISION';
  const unsavedIdentity = !!record && (dni.trim() !== record.dni || nombres.trim() !== record.nombres || apellidos.trim() !== record.apellidos || !!front || !!back);
  const final = ['VERIFICADO', 'APROBADO_MANUAL', 'RECHAZADO_MANUAL'].includes(record?.estado ?? '');

  return <section className="card">
    <h1>Verificación de identidad</h1>
    <p className="muted">Tus documentos se guardan en un espacio privado. Al completar los ocho dígitos del DNI, Petly buscará los nombres y apellidos para autocompletar. Verifica los datos antes de guardar.</p>
    <p className="muted">API Perú permite contrastar el DNI con los nombres y apellidos declarados. La coincidencia de datos no acredita por sí sola la titularidad del documento.</p>
    <Feedback error={error} success={success} />
    {record && <div className="card" aria-live="polite">
      <h2>Estado: {record.estado.replaceAll('_', ' ')}</h2>
      {record.fecha_consulta && <p>Consultado: {new Date(record.fecha_consulta).toLocaleString('es-PE')}</p>}
      {record.proveedor && <p>Proveedor: {record.proveedor}</p>}
      {record.referencia_reniec && <p>Referencia: {record.referencia_reniec}</p>}
      {record.error_servicio && <p role="alert">{record.error_servicio}</p>}
      {pendingReview && <p>La consulta requiere revisión administrativa. No se considera verificada hasta que se resuelva.</p>}
      {record.motivo_revision && <p>Resultado de revisión: {record.motivo_revision}</p>}
      {final && <p>La verificación está resuelta. Si necesitas corregir tus datos, comunícate con soporte.</p>}
    </div>}
    {!final && !pendingReview && <form onSubmit={event => { void upload(event); }}>
      <fieldset disabled={busy}>
        <label>DNI<input required inputMode="numeric" pattern="[0-9]{8}" maxLength={8} value={dni} onChange={e => { const value = e.target.value.replace(/\D/g, '').slice(0, 8); if (value !== dni) { lastLookupDni.current = ''; setNombres(''); setApellidos(''); setDniLookup(''); } setDni(value); }} /></label>
        {dniLookup && <p role="status" aria-live="polite">{dniLookup}</p>}
        <button type="button" className="secondary" disabled={busy || !/^[0-9]{8}$/.test(dni)} onClick={() => { lastLookupDni.current = ''; setNombres(''); setApellidos(''); setLookupAttempt(value => value + 1); }}>Consultar nombres por DNI</button>
        <label>Nombres como aparecen en el DNI<input required maxLength={100} value={nombres} onChange={e => setNombres(e.target.value)} /></label>
        <label>Apellidos como aparecen en el DNI<input required maxLength={100} value={apellidos} onChange={e => setApellidos(e.target.value)} /></label>
        <label>Foto del anverso (JPG o PNG, máximo 5 MB)<input required type="file" accept="image/jpeg,image/png" onChange={e => setFront(e.target.files?.[0] ?? null)} /></label>
        <label>Foto del reverso (JPG o PNG, máximo 5 MB)<input required type="file" accept="image/jpeg,image/png" onChange={e => setBack(e.target.files?.[0] ?? null)} /></label>
        {fieldErrors && <p role="alert">Cada imagen debe ser JPG o PNG y pesar como máximo 5 MB.</p>}
        <div className="actions"><button disabled={busy || fieldErrors}>{record ? 'Actualizar datos y documentos' : 'Guardar documentos privados'}</button>
          {record && <button type="button" className="secondary" onClick={() => void consult()} disabled={busy || pendingReview || unsavedIdentity}>Comprobar nombres y DNI</button>}</div>
      </fieldset>
    </form>}
    {record && !final && !pendingReview && <p className="muted">Guarda los datos y documentos primero. La comprobación utiliza los nombres y apellidos guardados.</p>}
    <button type="button" className="secondary" onClick={() => void load()} disabled={busy}>Actualizar estado</button>
  </section>;
}
