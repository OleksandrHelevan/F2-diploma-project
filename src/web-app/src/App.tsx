import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import Keycloak from 'keycloak-js'
import './App.css'

type User = {
  id: string
  username: string
  email: string
  firstName: string | null
  lastName: string | null
  role: string
  companyId: string | null
  active: boolean
}

type FormState = {
  username: string
  email: string
  firstName: string
  lastName: string
  role: string
  companyId: string
}

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

const keycloak = new Keycloak({
  url: 'http://localhost:8081',
  realm: 'bricklayers',
  clientId: 'bricklayers-web-app',
})

const emptyForm: FormState = {
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  role: 'BUILDER',
  companyId: '',
}

function App() {
  const [users, setUsers] = useState<User[]>([])
  const [form, setForm] = useState<FormState>(emptyForm)
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(false)
  const [token, setToken] = useState(localStorage.getItem('bricklayers_token') ?? '')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [authReady, setAuthReady] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)
  const [username, setUsername] = useState('')

  const getToken = () => token || localStorage.getItem('bricklayers_token') || ''

  const persistToken = (nextToken: string) => {
    setToken(nextToken)
    localStorage.setItem('bricklayers_token', nextToken)
  }

  const login = () => {
    void keycloak.login({ redirectUri: window.location.origin })
  }

  const register = () => {
    void keycloak.register({ redirectUri: window.location.origin })
  }

  const logout = () => {
    localStorage.removeItem('bricklayers_token')
    void keycloak.logout({ redirectUri: window.location.origin })
  }

  const loadUsers = async () => {
    setLoading(true)
    setStatus('Loading users...')

    try {
      const response = await fetch(`${API_BASE}/api/v1/users`, {
        headers: {
          Authorization: `Bearer ${getToken()}`,
          Accept: 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error(`Request failed with ${response.status}`)
      }

      const data = (await response.json()) as User[]
      setUsers(data)
      setStatus(`Loaded ${data.length} users`)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      setStatus(`Unable to load users. Add a valid JWT token first. (${message})`)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setLoading(true)

    try {
      const payload = {
        username: form.username,
        email: form.email,
        firstName: form.firstName || null,
        lastName: form.lastName || null,
        role: form.role,
        companyId: form.companyId || null,
      }

      const response = await fetch(`${API_BASE}/api/v1/users${editingId ? `/${editingId}` : ''}`, {
        method: editingId ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${getToken()}`,
        },
        body: JSON.stringify(payload),
      })

      if (!response.ok) {
        throw new Error(`Request failed with ${response.status}`)
      }

      setForm(emptyForm)
      setEditingId(null)
      setStatus(editingId ? 'User updated successfully' : 'User created successfully')
      await loadUsers()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      setStatus(`Request failed. ${message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleEdit = (user: User) => {
    setEditingId(user.id)
    setForm({
      username: user.username,
      email: user.email,
      firstName: user.firstName ?? '',
      lastName: user.lastName ?? '',
      role: user.role,
      companyId: user.companyId ?? '',
    })
    setStatus(`Editing ${user.username}`)
  }

  const handleDelete = async (id: string) => {
    try {
      const response = await fetch(`${API_BASE}/api/v1/users/${id}`, {
        method: 'DELETE',
        headers: {
          Authorization: `Bearer ${getToken()}`,
        },
      })

      if (!response.ok) {
        throw new Error(`Delete failed with ${response.status}`)
      }

      setStatus('User deleted successfully')
      await loadUsers()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      setStatus(`Delete failed. ${message}`)
    }
  }

  useEffect(() => {
    let mounted = true

    void keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
      .then((isAuthenticated) => {
        if (!mounted) {
          return
        }

        setAuthenticated(isAuthenticated)
        setAuthReady(true)

        if (isAuthenticated && keycloak.token) {
          persistToken(keycloak.token)
          setUsername(keycloak.tokenParsed?.preferred_username ?? 'user')
        }
      })
      .catch(() => {
        if (mounted) {
          setStatus('Unable to initialize Keycloak')
          setAuthReady(true)
        }
      })

    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).then(() => {
        if (keycloak.token) {
          persistToken(keycloak.token)
        }
      })
    }

    return () => {
      mounted = false
    }
  }, [])

  useEffect(() => {
    if (authenticated) {
      void loadUsers()
    }
  }, [authenticated])

  if (!authReady) {
    return <main className="auth-shell"><section className="auth-card"><p className="eyebrow">Bricklayers</p><h1>Connecting to Keycloak...</h1></section></main>
  }

  if (!authenticated) {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <p className="eyebrow">Bricklayers</p>
          <h1>User Service</h1>
          <p className="auth-copy">Sign in to manage users and access the protected API.</p>
          <div className="auth-actions">
            <button type="button" className="primary-button" onClick={login}>Sign in</button>
            <button type="button" className="secondary-button" onClick={register}>Create account</button>
          </div>
          {status && <p className="auth-error">{status}</p>}
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">Bricklayers</p>
          <h1>User Service</h1>
          <p className="signed-in">Signed in as {username}</p>
        </div>
        <div className="topbar-actions">
          <button type="button" className="secondary-button" onClick={() => void loadUsers()}>Refresh users</button>
          <button type="button" className="danger-button" onClick={logout}>Sign out</button>
        </div>
      </header>

      <section className="panel token-panel">
        <label htmlFor="token-input">JWT token</label>
        <div className="token-row">
          <input
            id="token-input"
            type="text"
            value={token}
            onChange={(event) => persistToken(event.target.value)}
            placeholder="Paste access token"
          />
          <button type="button" className="secondary-button" onClick={() => void loadUsers()}>
            Load
          </button>
        </div>
      </section>

      <section className="stats-grid">
        <article className="stat-card">
          <span>Total users</span>
          <strong>{users.length}</strong>
        </article>
        <article className="stat-card">
          <span>Active roles</span>
          <strong>{new Set(users.map((user) => user.role)).size}</strong>
        </article>
        <article className="stat-card">
          <span>API</span>
          <strong>REST</strong>
        </article>
      </section>

      <section className="panel form-panel">
        <h2>{editingId ? 'Edit user' : 'Create user'}</h2>
        <form className="user-form" onSubmit={handleSubmit}>
          <input
            value={form.username}
            onChange={(event) => setForm({ ...form, username: event.target.value })}
            placeholder="Username"
          />
          <input
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            placeholder="Email"
            type="email"
          />
          <input
            value={form.firstName}
            onChange={(event) => setForm({ ...form, firstName: event.target.value })}
            placeholder="First name"
          />
          <input
            value={form.lastName}
            onChange={(event) => setForm({ ...form, lastName: event.target.value })}
            placeholder="Last name"
          />
          <select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value })}>
            <option value="BUILDER">BUILDER</option>
            <option value="SUPERADMIN">SUPERADMIN</option>
            <option value="CUSTOMER">CUSTOMER</option>
            <option value="FOREMAN">FOREMAN</option>
            <option value="SUPPLIER">SUPPLIER</option>
          </select>
          <input
            value={form.companyId}
            onChange={(event) => setForm({ ...form, companyId: event.target.value })}
            placeholder="Company ID (UUID)"
          />
          <div className="form-actions">
            <button type="submit" disabled={loading} className="primary-button">
              {loading ? 'Saving...' : editingId ? 'Update' : 'Create'}
            </button>
            {editingId && (
              <button type="button" className="secondary-button" onClick={() => {
                setEditingId(null)
                setForm(emptyForm)
                setStatus('Edit cancelled')
              }}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Users</h2>
          <span>{status || 'Awaiting API response'}</span>
        </div>

        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Email</th>
              <th>Role</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.length === 0 ? (
              <tr>
                <td colSpan={5}>No users yet.</td>
              </tr>
            ) : (
              users.map((user) => (
                <tr key={user.id}>
                  <td>{user.username}</td>
                  <td>{user.email}</td>
                  <td>{user.role}</td>
                  <td>
                    <span className={user.active ? 'status status-active' : 'status status-inactive'}>
                      {user.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="table-actions">
                    <button type="button" className="small-button info-button" onClick={() => handleEdit(user)}>
                      Edit
                    </button>
                    <button type="button" className="small-button danger-button" onClick={() => void handleDelete(user.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </section>
    </main>
  )
}

export default App
