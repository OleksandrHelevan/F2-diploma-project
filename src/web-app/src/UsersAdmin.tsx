import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { apiFetch } from './auth'
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

const emptyForm: FormState = {
  username: '',
  email: '',
  firstName: '',
  lastName: '',
  role: 'BUILDER',
  companyId: '',
}

type UsersResult = {
  users?: User[]
  status: string
}

async function fetchUsers(): Promise<UsersResult> {
  try {
    const response = await apiFetch('/api/v1/admin/users?page=0&size=100')
    const data = (await response.json()) as { content: User[] }
    return { users: data.content, status: `Loaded ${data.content.length} users` }
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error'
    return { status: `Unable to load users. (${message})` }
  }
}

type UsersAdminProps = {
  username: string
  onLogout: () => void
}

function UsersAdmin({ username, onLogout }: UsersAdminProps) {
  const [users, setUsers] = useState<User[]>([])
  const [form, setForm] = useState<FormState>(emptyForm)
  const [status, setStatus] = useState('Loading users...')
  const [loading, setLoading] = useState(true)
  const [editingId, setEditingId] = useState<string | null>(null)

  const showUsers = useCallback((result: UsersResult) => {
    if (result.users) {
      setUsers(result.users)
    }
    setStatus(result.status)
    setLoading(false)
  }, [])

  const loadUsers = async () => {
    setLoading(true)
    setStatus('Loading users...')
    showUsers(await fetchUsers())
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setLoading(true)

    try {
      const common = {
        email: form.email,
        firstName: form.firstName || null,
        lastName: form.lastName || null,
        role: form.role,
        companyId: form.companyId || null,
      }
      const payload = editingId ? common : { username: form.username, ...common }

      await apiFetch(`/api/v1/admin/users${editingId ? `/${editingId}` : ''}`, {
        method: editingId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })

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
      await apiFetch(`/api/v1/admin/users/${id}`, { method: 'DELETE' })
      setStatus('User deleted successfully')
      await loadUsers()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unknown error'
      setStatus(`Delete failed. ${message}`)
    }
  }

  useEffect(() => {
    let active = true
    void fetchUsers().then((result) => {
      if (active) {
        showUsers(result)
      }
    })
    return () => {
      active = false
    }
  }, [showUsers])

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
          <button type="button" className="danger-button" onClick={onLogout}>Sign out</button>
        </div>
      </header>

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
            disabled={editingId !== null}
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

export default UsersAdmin
