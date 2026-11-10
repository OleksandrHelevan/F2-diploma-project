import { useEffect, useState } from 'react'
import { keycloak, realmRoles } from './auth'
import ChatPage from './ChatPage'
import UsersAdmin from './UsersAdmin'
import './App.css'

type AuthState = 'loading' | 'anonymous' | 'authenticated' | 'error'

// keycloak-js may only be initialised once per page, StrictMode would otherwise run init twice.
const keycloakInit = keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })

function App() {
  const [auth, setAuth] = useState<AuthState>('loading')

  useEffect(() => {
    let mounted = true
    keycloakInit
      .then((authenticated) => {
        if (mounted) {
          setAuth(authenticated ? 'authenticated' : 'anonymous')
        }
      })
      .catch(() => {
        if (mounted) {
          setAuth('error')
        }
      })
    return () => {
      mounted = false
    }
  }, [])

  const login = () => {
    void keycloak.login({ redirectUri: window.location.origin })
  }

  const register = () => {
    void keycloak.register({ redirectUri: window.location.origin })
  }

  const logout = () => {
    void keycloak.logout({ redirectUri: window.location.origin })
  }

  if (auth === 'loading') {
    return <main className="auth-shell"><section className="auth-card"><p className="eyebrow">Bricklayers</p><h1>Connecting to Keycloak...</h1></section></main>
  }

  if (auth !== 'authenticated') {
    return (
      <main className="auth-shell">
        <section className="auth-card">
          <p className="eyebrow">Bricklayers</p>
          <h1>Team Chat</h1>
          <p className="auth-copy">Sign in to chat with your team.</p>
          <div className="auth-actions">
            <button type="button" className="primary-button" onClick={login}>Sign in</button>
            <button type="button" className="secondary-button" onClick={register}>Create account</button>
          </div>
          {auth === 'error' && <p className="auth-error">Unable to initialize Keycloak</p>}
        </section>
      </main>
    )
  }

  // A superadmin has no company and therefore no chats, so they land on user management.
  if (realmRoles().includes('SUPERADMIN')) {
    return <UsersAdmin username={keycloak.tokenParsed?.preferred_username ?? 'user'} onLogout={logout} />
  }

  return <ChatPage onLogout={logout} />
}

export default App
