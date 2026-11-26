import { useEffect } from 'react';
import { animate, motion, useMotionValue, useTransform } from 'framer-motion';
import { useReducedMotionPreference } from './useReducedMotionPreference';

export const landingArt = '/images/acceso';
const cycle = 14;
const rightWalkEnds = 5.8;
const leftWalkStarts = 7.2;
const leftWalkEnds = 13;

function kittenFrame(time: number) {
  if (time < rightWalkEnds) return Math.floor(time * 8) % 4;
  if (time < 6.35) return 4;
  if (time < leftWalkStarts) return 5 + Math.min(2, Math.floor((time - 6.35) * 4));
  if (time < leftWalkEnds) return Math.floor((time - leftWalkStarts) * 8) % 4;
  if (time < 13.4) return 4;
  return 5 + Math.min(2, Math.floor((time - 13.4) * 5));
}

export function WalkingKitten() {
  const reduced = useReducedMotionPreference();
  const time = useMotionValue(0);
  const x = useTransform(
    time,
    [0, rightWalkEnds, leftWalkStarts, leftWalkEnds, cycle],
    ['0%', '100%', '100%', '0%', '0%']
  );
  const scaleX = useTransform(time, value => (value >= leftWalkStarts ? -1 : 1));
  const backgroundPosition = useTransform(time, value => {
    const frame = kittenFrame(value);
    return `${((frame % 4) / 3) * 100}% ${frame < 4 ? 0 : 100}%`;
  });

  useEffect(() => {
    if (reduced) return;
    time.set(0);
    const playback = animate(time, cycle, { duration: cycle, ease: 'linear', repeat: Infinity });
    return () => playback.stop();
  }, [reduced, time]);

  return (
    <div className="landing__kitten-lane" aria-hidden="true">
      <motion.div className="landing__kitten-travel" style={{ x: reduced ? '30%' : x }}>
        <motion.div
          className="landing__kitten"
          style={{
            backgroundPosition: reduced ? '0% 100%' : backgroundPosition,
            scaleX: reduced ? 1 : scaleX,
          }}
        />
      </motion.div>
    </div>
  );
}

export function LandingPlant({ side }: { side: 'left' | 'right' }) {
  const reduced = useReducedMotionPreference();
  const right = side === 'right';
  return (
    <div className={`landing__plant landing__plant--${side}`} aria-hidden="true">
      <motion.img
        className="landing__foliage"
        alt=""
        src={`${landingArt}/follaje.webp`}
        animate={{ rotate: reduced ? 0 : [0, 3, 0, -3, 0] }}
        transition={
          reduced
            ? { duration: 0 }
            : { duration: 6, delay: right ? 0.8 : 0, repeat: Infinity, ease: 'easeInOut' }
        }
      />
      <img className="landing__pot" src={`${landingArt}/maceta.webp`} alt="" />
    </div>
  );
}
