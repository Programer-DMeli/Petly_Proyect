export type RespuestasAdopcion = Record<string, string | boolean>;

type Campo = {
  nombre: string;
  etiqueta: string;
  pregunta?: string;
  tipo: 'texto' | 'dni' | 'telefono' | 'area' | 'opciones' | 'booleano';
  opciones?: readonly string[];
  cuando?: (respuestas: RespuestasAdopcion) => boolean;
};

export const seccionesFormularioAdopcion: readonly {
  titulo: string;
  campos: readonly Campo[];
}[] = [
  {
    titulo: 'Datos de contacto',
    campos: [
      { nombre: 'nombreCompleto', etiqueta: 'Nombre completo', tipo: 'texto' },
      {
        nombre: 'dni',
        etiqueta: 'DNI declarado',
        pregunta: 'DNI declarado (8 dígitos)',
        tipo: 'dni',
      },
      { nombre: 'direccionHogar', etiqueta: 'Dirección del hogar', tipo: 'texto' },
      { nombre: 'telefonoContacto', etiqueta: 'Teléfono de contacto', tipo: 'telefono' },
    ],
  },
  {
    titulo: 'Vivienda',
    campos: [
      {
        nombre: 'tipoPropiedadVivienda',
        etiqueta: 'Propiedad de vivienda',
        tipo: 'opciones',
        opciones: ['Propia', 'Alquilada'],
      },
      {
        nombre: 'permisoPropietarioDetalle',
        etiqueta: 'Permiso del propietario',
        tipo: 'texto',
        pregunta: '¿Cuentas con permiso del propietario para tener una mascota?',
        cuando: respuestas => respuestas['tipoPropiedadVivienda'] === 'Alquilada',
      },
      {
        nombre: 'espaciosDisponiblesMascota',
        etiqueta: 'Espacios disponibles',
        tipo: 'texto',
        pregunta: '¿Qué espacios tendrá disponibles la mascota?',
      },
    ],
  },
  {
    titulo: 'Familia y convivencia',
    campos: [
      {
        nombre: 'tieneAlergias',
        etiqueta: 'Alergias',
        tipo: 'booleano',
        pregunta: '¿Alguien en tu hogar tiene alergia a los animales?',
      },
      {
        nombre: 'alergiasDetalle',
        etiqueta: 'Detalle de alergias',
        tipo: 'texto',
        cuando: respuestas => respuestas['tieneAlergias'] === true,
      },
      {
        nombre: 'vivesConPersonasEspeciales',
        etiqueta: 'Personas que requieren cuidados',
        tipo: 'booleano',
        pregunta:
          '¿Vives con niños pequeños, adultos mayores o personas que requieren cuidados especiales?',
      },
      {
        nombre: 'personasEspecialesDetalle',
        etiqueta: 'Detalle de convivencia',
        tipo: 'texto',
        cuando: respuestas => respuestas['vivesConPersonasEspeciales'] === true,
      },
    ],
  },
  {
    titulo: 'Motivación y experiencia',
    campos: [
      {
        nombre: 'motivoAdopcion',
        etiqueta: 'Motivo de adopción',
        tipo: 'area',
        pregunta: '¿Por qué quieres adoptar?',
      },
      {
        nombre: 'planCuidado',
        etiqueta: 'Plan de cuidados',
        tipo: 'area',
        pregunta: '¿Cómo organizarás sus cuidados diarios?',
      },
      {
        nombre: 'haCuidadoMascotasAntes',
        etiqueta: 'Experiencia previa',
        tipo: 'booleano',
        pregunta: '¿Has cuidado mascotas antes?',
      },
      {
        nombre: 'experienciaPreviaDetalle',
        etiqueta: 'Detalle de experiencia',
        tipo: 'area',
        pregunta: 'Si tuviste mascotas, ¿siguen contigo? Si no, ¿qué ocurrió?',
        cuando: respuestas => respuestas['haCuidadoMascotasAntes'] === true,
      },
    ],
  },
  {
    titulo: 'Compromisos de adopción',
    campos: [
      {
        nombre: 'aceptaEsterilizacion',
        etiqueta: 'Acepta esterilización',
        tipo: 'booleano',
        pregunta: '¿Aceptas la esterilización según indicación veterinaria?',
      },
      {
        nombre: 'aceptaSeguimiento',
        etiqueta: 'Acepta seguimiento',
        tipo: 'booleano',
        pregunta: '¿Aceptas el seguimiento mediante fotos, visitas o videollamadas?',
      },
      {
        nombre: 'puedeCubrirGastosVeterinarios',
        etiqueta: 'Puede cubrir gastos veterinarios',
        tipo: 'booleano',
        pregunta: '¿Puedes cubrir los gastos de alimentación y atención veterinaria?',
      },
    ],
  },
];
