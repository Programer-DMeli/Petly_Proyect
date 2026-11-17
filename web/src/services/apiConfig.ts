export const springBase = (
  (import.meta.env['VITE_API_USUARIO_URL'] as string | undefined) ?? 'http://localhost:8080'
).replace(/\/$/, '');

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public retryAfterSeconds = 0
  ) {
    super(message);
  }
}
