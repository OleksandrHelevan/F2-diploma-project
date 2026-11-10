import Keycloak from 'keycloak-js'

export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export const keycloak = new Keycloak({
  url: 'http://localhost:8084',
  realm: 'bricklayers',
  clientId: 'bricklayers-web-app',
})

export async function getToken(): Promise<string> {
  await keycloak.updateToken(30)
  return keycloak.token ?? ''
}

export function realmRoles(): string[] {
  return keycloak.tokenParsed?.realm_access?.roles ?? []
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('Authorization', `Bearer ${await getToken()}`)
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response))
  }
  return response
}

async function errorMessage(response: Response): Promise<string> {
  const fallback = `Запит завершився з кодом ${response.status}`
  const body = await response.text()
  try {
    const parsed = JSON.parse(body) as { message?: unknown }
    return typeof parsed.message === 'string' && parsed.message ? parsed.message : fallback
  } catch {
    return body || fallback
  }
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}
