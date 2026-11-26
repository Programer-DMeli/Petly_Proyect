import { motion } from 'framer-motion';
import { Link } from 'react-router-dom';
import { useReducedMotionPreference } from '../useReducedMotionPreference';

const steps = [
  {
    title: 'Ingresa a Petly',
    description:
      'Inicia sesión con tu correo y contraseña o crea una cuenta. En «Mi perfil», completa el cuestionario sobre tu hogar y estilo de vida para orientar tu búsqueda.',
    icon: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  },
  {
    title: 'Encuentra a tu compañero',
    description:
      'Explora el catálogo y abre la ficha de una mascota para conocer su historia y sus necesidades. Desde allí puedes identificar al albergue y escribirle si tienes dudas.',
    icon: 'M21 21l-4.4-4.4M18 10.5a7.5 7.5 0 1 1-15 0 7.5 7.5 0 0 1 15 0Z',
  },
  {
    title: 'Solicita la adopción',
    description:
      'Si la mascota está disponible, completa y envía la solicitud desde su ficha. En «Mis solicitudes» podrás ver su estado y las actualizaciones del albergue.',
    icon: 'M13 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10m-7-7 7 7m-7-7v7h7m-12 5 2 2 4-4',
  },
  {
    title: 'Continúa después de la adopción',
    description:
      'En «Mi adopción», abre «Seguimiento postadopción» para consultar el cronograma de visitas, llamadas y reportes. Si tienes un reporte pendiente, puedes enviar fotos y contarle al albergue cómo se adapta tu mascota.',
    icon: 'M20.5 4.9a5 5 0 0 0-7.1 0L12 6.3l-1.4-1.4a5 5 0 0 0-7.1 7.1L12 20.5l8.5-8.5a5 5 0 0 0 0-7.1Z',
  },
];

function AdoptionStep({
  step,
  index,
  reducedMotion,
}: {
  step: (typeof steps)[number];
  index: number;
  reducedMotion: boolean;
}) {
  return (
    <motion.li
      className="petly-guide__step"
      initial={reducedMotion ? false : { opacity: 0.8, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={reducedMotion ? { duration: 0 } : { duration: 0.34, delay: index * 0.08 }}
    >
      <div className="petly-guide__marker">
        <span aria-hidden="true">{index + 1}</span>
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
          width="25"
          height="25"
        >
          <path d={step.icon} />
        </svg>
      </div>
      <h2>{step.title}</h2>
      <p>{step.description}</p>
    </motion.li>
  );
}

export default function ConocePetlyPage() {
  const reducedMotion = useReducedMotionPreference();

  return (
    <section className="petly-guide" aria-labelledby="petly-guide-title">
      <header className="petly-guide__header">
        <h1 id="petly-guide-title">Conoce Petly</h1>
        <p>Descubre cómo adoptar y acompañar a tu mascota en su nuevo hogar.</p>
      </header>
      <ol className="petly-guide__journey" role="list">
        {steps.map((step, index) => (
          <AdoptionStep key={step.title} step={step} index={index} reducedMotion={reducedMotion} />
        ))}
      </ol>
      <Link className="button petly-guide__cta" to="/">
        Ir al inicio
      </Link>
    </section>
  );
}
