import { useCallback, useEffect, useState, type FormEvent } from 'react';
import Feedback from '../../../../components/Feedback';
import { django, errorMessage, json, spring } from '../../../../services/http';

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
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    setError('');
    try {
      const data = await django<VerificacionKyc[] | Page<VerificacionKyc>>('/api/acceso/verificaciones-kyc/');
      const item = firstRecord(data) ?? null;
      setRecord(item);
      if (item) {
        setDni(item.dni);
        setNombres(item.nombres);
        setApellidos(item.apellidos);
      }
    } catch (e) { setError(errorMessage(e)); }
  }, []);

  useEffect(() => { void load(); }, [load]);

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
      setSuccess(result.coincide ? 'RENIEC confirmó la coincidencia de identidad.' : 'La identidad quedó pendiente de revisión manual.');
    } catch (e) { setError(errorMessage(e)); await load(); }
    finally { setBusy(false); }
  }

  const fieldErrors = [front, back].some(file => file && (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)));
  const pendingReview = record?.estado === 'PENDIENTE_REVISION';
  const final = ['VERIFICADO', 'APROBADO_MANUAL', 'RECHAZADO_MANUAL'].includes(record?.estado ?? '');

  return <section className="card">
    <h1>Verificación de identidad</h1>
    <p className="muted">Tus documentos se guardan en un espacio privado. La consulta compara los datos ingresados con el proveedor autorizado configurado por Petly.</p>
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
    {!final && <form onSubmit={upload}>
      <fieldset disabled={busy}>
        <label>DNI<input required inputMode="numeric" pattern="[0-9]{8}" maxLength={8} value={dni} onChange={e => setDni(e.target.value)} /></label>
        <label>Nombres como aparecen en el DNI<input required maxLength={100} value={nombres} onChange={e => setNombres(e.target.value)} /></label>
        <label>Apellidos como aparecen en el DNI<input required maxLength={100} value={apellidos} onChange={e => setApellidos(e.target.value)} /></label>
        <label>Foto del anverso (JPG o PNG, máximo 5 MB)<input required type="file" accept="image/jpeg,image/png" onChange={e => setFront(e.target.files?.[0] ?? null)} /></label>
        <label>Foto del reverso (JPG o PNG, máximo 5 MB)<input required type="file" accept="image/jpeg,image/png" onChange={e => setBack(e.target.files?.[0] ?? null)} /></label>
        {fieldErrors && <p role="alert">Cada imagen debe ser JPG o PNG y pesar como máximo 5 MB.</p>}
        <div className="actions"><button disabled={busy || fieldErrors}>{record ? 'Actualizar datos y documentos' : 'Guardar documentos privados'}</button>
          {record && <button type="button" className="secondary" onClick={() => void consult()} disabled={busy || pendingReview}>Consultar RENIEC</button>}</div>
      </fieldset>
    </form>}
    {record && !final && !pendingReview && <p className="muted">Guarda los documentos primero. Después podrás iniciar la consulta.</p>}
    <button type="button" className="secondary" onClick={() => void load()} disabled={busy}>Actualizar estado</button>
  </section>;
}
