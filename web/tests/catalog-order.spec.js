import { test, expect } from '@playwright/test';

const pets = [
  {
    id: 4,
    nombre: 'Mejor puntaje',
    especie: 'Perro',
    tamano: 'Mediano',
    edadMeses: 24,
    temperamento: 'Sociable',
    energia: 'Media',
    descripcion: '',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Disponible',
    fotografiaUrl: null,
    compatibilidad: { porcentaje: 100, puntaje: 100, cobertura: 100, datosInsuficientes: false },
  },
  {
    id: 7,
    nombre: 'Segundo puntaje',
    especie: 'Perro',
    tamano: 'Pequeño',
    edadMeses: 12,
    temperamento: 'Tranquilo',
    energia: 'Baja',
    descripcion: '',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Disponible',
    fotografiaUrl: null,
    compatibilidad: { porcentaje: 72, puntaje: 72, cobertura: 100, datosInsuficientes: false },
  },
  {
    id: 9,
    nombre: 'Datos incompletos',
    especie: 'Gato',
    tamano: 'Por evaluar',
    edadMeses: null,
    temperamento: 'Por evaluar',
    energia: 'Por evaluar',
    descripcion: '',
    albergue: 'Hogar Petly',
    albergueId: 1,
    ubicacion: 'Lima',
    estado: 'Disponible',
    fotografiaUrl: null,
    compatibilidad: { porcentaje: null, puntaje: null, cobertura: 0, datosInsuficientes: true },
  },
];

test('el catálogo React conserva el orden recibido y marca compatibilidad incompleta', async ({ page }) => {
  await page.route('http://localhost:8080/api/perfil/favoritos', route => route.fulfill({ json: [] }));
  await page.route('http://localhost:8080/api/auth/login', route =>
    route.fulfill({ json: { token: 'token-de-prueba', expiresIn: 3600 } })
  );
  await page.route('http://localhost:8080/api/auth/me', route =>
    route.fulfill({ json: { id: '7', correo: 'angie@example.com', rol: 'ADOPTANTE' } })
  );
  await page.route('http://localhost:8080/api/catalogo/opciones', route =>
    route.fulfill({ json: { especies: ['Perro', 'Gato'], tamanos: ['Mediano'], temperamentos: ['Sociable'] } })
  );
  await page.route('http://localhost:8080/api/catalogo?**', route =>
    route.fulfill({ json: { items: pets, total: pets.length } })
  );

  await page.goto('/acceso');
  await page.getByLabel('Correo', { exact: true }).fill('angie@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill('password-de-prueba');
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();

  const cards = page.locator('article.pet');
  await expect(cards).toHaveCount(3);
  await expect(cards.locator('h2')).toHaveText([
    'Mejor puntaje',
    'Segundo puntaje',
    'Datos incompletos',
  ]);
  await expect(cards.nth(2).getByText('Información insuficiente', { exact: true })).toBeVisible();
});
