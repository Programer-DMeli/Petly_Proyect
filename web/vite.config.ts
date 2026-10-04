import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Petly web — Vite + React + TypeScript.
// Puerto 5173 indicado en PLANNING.md v2 §11.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
  preview: {
    port: 5173,
  },
})
