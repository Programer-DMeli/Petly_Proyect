import { Outlet } from 'react-router-dom';

/**
 * Administración general mínima: gestión de albergues.
 * No confundir con el personal del albergue (PLANNING v2 §5).
 */
export default function AdminLayout() {
  return (
    <div className="layout">
      <Outlet />
    </div>
  );
}
