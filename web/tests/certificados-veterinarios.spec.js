import { test, expect } from '@playwright/test';

for (const propietario of [true, false]) {
  test(propietario ? 'muestra los dos tipos y bloquea la carga con el perfil pendiente'
    : 'otro albergue no puede ver los metadatos ni abrir el documento', async ({ page }) => {
    const usuario = { id: propietario ? '8' : '9', correo: 'albergue@example.test', rol: 'ALBERGUE' };
    const mascota = {
      id: 1, nombre: 'Nube', especie: 'GATO', sexo: 'HEMBRA', tamanio: 'MEDIANO',
      temperamento: 'SOCIABLE', energia: 'MEDIA', edad_anos: 2, edad_meses: 0,
      descripcion: '', estado: 'DISPONIBLE', alerta_clinica: 'Cuidados especiales.',
      evidencia_vacunacion: '/media/mascotas/vacunacion.png',
    };
    const consultas = [];
    await page.route('**/media/mascotas/vacunacion.png', route => route.fulfill({
      contentType: 'image/png',
      body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=', 'base64'),
    }));
    await page.route('**/api/**', async route => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      if (path === '/api/auth/refresh') return route.fulfill({ status: 401 });
      if (path === '/api/auth/login') return route.fulfill({ json: { token: `prueba-${usuario.id}`, expiresIn: 3600, user: usuario } });
      if (path === '/api/auth/me') return route.fulfill({ json: usuario });
      if (path === '/api/albergues/mi-perfil/') return route.fulfill({ json: {
        id: 1, nombre: 'Refugio', direccion: 'Lima', telefono: '999000111', email: usuario.correo,
      } });
      if (path === '/api/mascotas/1/') return route.fulfill({ json: mascota });
      if (path.includes('/certificados-veterinarios/')) {
        consultas.push(path);
        expect(request.headers().authorization).toBe(`Bearer prueba-${usuario.id}`);
        expect(request.method()).toBe('GET');
        return propietario ? route.fulfill({ json: {
          carga_habilitada: false,
          mensaje: 'La carga de certificados está suspendida hasta que se defina el perfil de firma digital.',
          certificados: [{ tipo: 'VACUNACION', validacion: 'PENDIENTE' }],
        } }) : route.fulfill({ status: 404, json: { detail: 'No encontrado.' } });
      }
      return route.fulfill({ json: { results: path === '/api/mascotas/' ? [mascota] : [], next: null } });
    });

    await page.goto('/acceso');
    await page.getByLabel('Correo', { exact: true }).fill(usuario.correo);
    await page.getByLabel('Contraseña', { exact: true }).fill('clave-de-prueba');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page).toHaveURL(/\/albergue$/);
    await page.evaluate(() => {
      window.history.pushState({}, '', '/albergue/mascotas');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });

    const editar = page.getByRole('button', { name: 'Editar mascota', exact: true });
    await editar.click();
    const certificados = page.getByRole('region', { name: 'Certificados veterinarios' });
    if (propietario) {
      await expect(certificados.getByText(/carga de certificados está suspendida/)).toBeVisible();
      const vacunacion = certificados.getByRole('region', { name: 'Certificado de vacunación' });
      const esterilizacion = certificados.getByRole('region', { name: 'Certificado de esterilización' });
      await expect(vacunacion.getByText('Firma pendiente de verificación.')).toBeVisible();
      await expect(esterilizacion.getByText('Sin certificado guardado.')).toBeVisible();
      await expect(vacunacion.getByLabel(/Certificado de vacunación/)).toBeDisabled();
      await expect(esterilizacion.getByLabel(/Certificado de esterilización/)).toBeDisabled();
      await expect(vacunacion.getByRole('button', { name: 'Reemplazar certificado' })).toBeDisabled();
      await expect(esterilizacion.getByRole('button', { name: 'Adjuntar certificado' })).toBeDisabled();
      await expect(page.getByRole('textbox', { name: 'Alerta clínica' })).toHaveValue('Cuidados especiales.');
      await expect(page.getByAltText('Vista previa: evidencia de vacunación')).toBeVisible();
      await page.getByRole('button', { name: 'Cancelar edición' }).click();
      await editar.click();
      await expect(vacunacion.getByText('Firma pendiente de verificación.')).toBeVisible();
    } else {
      await expect(certificados.getByRole('alert')).toHaveText('No encontrado.');
      await expect(certificados.getByText('Firma pendiente de verificación.')).toHaveCount(0);
    }
    await expect(certificados.getByRole('button', { name: 'Ver certificado' })).toHaveCount(0);
    expect(consultas).toEqual(Array(propietario ? 2 : 1).fill('/api/mascotas/1/certificados-veterinarios/'));
  });
}
