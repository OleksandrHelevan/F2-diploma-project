import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Client } from '@stomp/stompjs'
import { ApiError, apiFetch, getToken } from './auth'
import './Chat.css'

type Profile = {
  id: string
  username: string
  firstName: string | null
  lastName: string | null
  role: string
  companyId: string | null
}

type Participant = {
  userId: string
  role: string
  leftAt: string | null
}

type Chat = {
  id: string
  type: string
  name: string | null
  participants: Participant[]
  lastMessagePreview: string | null
  lastMessageAt: string | null
  unreadCount: number
  archived: boolean
}

type Message = {
  id: string
  senderId: string
  senderName: string
  type: string
  content: string | null
  timestamp: string
  deleted: boolean
  likesCount: number
  likedByMe: boolean
}

type ChatEvent = {
  type: string
  userId: string | null
  payload: unknown
}

type Page<T> = {
  content: T[]
}

const roleLabels: Record<string, string> = {
  SUPERADMIN: 'Адміністратор',
  CUSTOMER: 'Замовник',
  FOREMAN: 'Прораб',
  BUILDER: 'Будівельник',
  SUPPLIER: 'Постачальник',
}

const chatTypeLabels: Record<string, string> = {
  CUSTOMER_FOREMAN: 'ПРОЄКТ',
  FOREMAN_BUILDER: 'ОСОБИСТИЙ',
  FOREMAN_SUPPLIER: 'ПОСТАЧАННЯ',
  BRIGADE_ALL: 'БРИГАДА',
}

function displayName(user: Pick<Profile, 'firstName' | 'lastName' | 'username'>): string {
  const name = [user.firstName, user.lastName].filter(Boolean).join(' ')
  return name || user.username
}

function byTimestamp(a: Message, b: Message): number {
  return Date.parse(a.timestamp) - Date.parse(b.timestamp)
}

type ChatPageProps = {
  onLogout: () => void
}

function ChatPage({ onLogout }: ChatPageProps) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [colleagues, setColleagues] = useState<Profile[]>([])
  const [chats, setChats] = useState<Chat[]>([])
  const [chatId, setChatId] = useState<string | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [connection, setConnection] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const [typing, setTyping] = useState(false)
  const [error, setError] = useState('')
  const [blocked, setBlocked] = useState('')
  const [loading, setLoading] = useState(true)
  const clientRef = useRef<Client | null>(null)
  const typingTimerRef = useRef<number | undefined>(undefined)

  const chat = chats.find((item) => item.id === chatId) ?? null

  const namesById = useMemo(() => {
    const names = new Map(colleagues.map((user) => [user.id, displayName(user)]))
    if (profile) {
      names.set(profile.id, displayName(profile))
    }
    return names
  }, [colleagues, profile])

  const chatTitle = useCallback((item: Chat) => {
    if (item.name) {
      return item.name
    }
    const others = item.participants
      .filter((participant) => participant.userId !== profile?.id && !participant.leftAt)
      .map((participant) => namesById.get(participant.userId) ?? roleLabels[participant.role] ?? 'Учасник')
    return others.join(', ') || 'Чат'
  }, [namesById, profile?.id])

  const loadChats = useCallback(async () => {
    const response = await apiFetch('/api/v1/chats?page=0&size=50')
    const page = (await response.json()) as Page<Chat>
    setChats(page.content)
    return page.content
  }, [])

  useEffect(() => {
    let active = true

    const load = async () => {
      try {
        const [me, team] = await Promise.all([
          apiFetch('/api/v1/me').then((response) => response.json() as Promise<Profile>),
          apiFetch('/api/v1/users').then((response) => response.json() as Promise<Profile[]>),
        ])
        if (!active) {
          return
        }
        setProfile(me)
        setColleagues(team)
        const loaded = await loadChats()
        if (active && loaded.length > 0) {
          setChatId((current) => current ?? loaded[0].id)
          setConnection('connecting')
        }
      } catch (cause) {
        if (!active) {
          return
        }
        if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
          setBlocked('Ваш профіль ще не прив’язаний до компанії або ролі, тому чат недоступний. '
            + 'Зверніться до адміністратора або зачекайте синхронізації з Keycloak (до хвилини).')
        } else {
          setError(cause instanceof Error ? cause.message : 'Не вдалося завантажити чати')
        }
      } finally {
        if (active) {
          setLoading(false)
        }
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [loadChats])

  useEffect(() => {
    if (!chatId || !profile) {
      return
    }

    let active = true

    void apiFetch(`/api/v1/chats/${chatId}/messages?page=0`)
      .then(async (response) => {
        const page = (await response.json()) as { messages: Message[] }
        if (active) {
          setMessages([...page.messages].sort(byTimestamp))
          await apiFetch(`/api/v1/chats/${chatId}/read`, { method: 'POST' })
          setChats((current) => current.map((item) => item.id === chatId ? { ...item, unreadCount: 0 } : item))
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Не вдалося завантажити повідомлення')
        }
      })

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const client = new Client({
      brokerURL: `${protocol}//${window.location.host}/ws`,
      reconnectDelay: 3000,
      beforeConnect: async () => {
        client.connectHeaders = { Authorization: `Bearer ${await getToken()}` }
      },
      onConnect: () => {
        if (!active) {
          return
        }
        setConnection('connected')
        client.subscribe(`/topic/chats/${chatId}`, (frame) => {
          const event = JSON.parse(frame.body) as ChatEvent
          if (event.type === 'NEW_MESSAGE' || event.type === 'MESSAGE_EDITED') {
            const message = event.payload as Message
            setMessages((current) => [...current.filter((item) => item.id !== message.id), message].sort(byTimestamp))
          } else if (event.type === 'MESSAGE_DELETED' && typeof event.payload === 'string') {
            setMessages((current) => current.map((item) => (
              item.id === event.payload ? { ...item, deleted: true, content: null } : item
            )))
          } else if (event.type === 'LIKE_UPDATE' && typeof event.payload === 'object' && event.payload !== null) {
            const update = event.payload as { messageId: string; likesCount: number }
            setMessages((current) => current.map((item) => (
              item.id === update.messageId ? { ...item, likesCount: update.likesCount } : item
            )))
          } else if (event.type === 'TYPING' && event.userId !== profile.id) {
            setTyping(true)
          } else if (event.type === 'STOPPED_TYPING' && event.userId !== profile.id) {
            setTyping(false)
          }
        })
      },
      onWebSocketClose: () => {
        if (active) {
          setConnection('disconnected')
        }
      },
      onStompError: (frame) => {
        if (active) {
          setError(frame.headers.message || 'Помилка WebSocket')
        }
      },
    })
    clientRef.current = client
    client.activate()

    return () => {
      active = false
      window.clearTimeout(typingTimerRef.current)
      clientRef.current = null
      void client.deactivate()
    }
  }, [chatId, profile])

  const selectChat = (id: string) => {
    if (id === chatId) {
      return
    }
    setChatId(id)
    setMessages([])
    setTyping(false)
    setError('')
    setConnection('connecting')
  }

  const startDirectChat = async (colleague: Profile) => {
    setError('')
    try {
      const response = await apiFetch('/api/v1/chats/direct', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          type: colleague.role === 'SUPPLIER' ? 'FOREMAN_SUPPLIER' : 'FOREMAN_BUILDER',
          targetUserId: colleague.id,
        }),
      })
      const created = (await response.json()) as Chat
      await loadChats()
      selectChat(created.id)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не вдалося створити чат')
    }
  }

  const sendTyping = (isTyping: boolean) => {
    if (!chatId || clientRef.current?.connected !== true) {
      return
    }
    clientRef.current.publish({
      destination: `/app/chats/${chatId}/typing`,
      body: JSON.stringify({ typing: isTyping }),
    })
  }

  const handleDraftChange = (value: string) => {
    setDraft(value)
    sendTyping(true)
    window.clearTimeout(typingTimerRef.current)
    typingTimerRef.current = window.setTimeout(() => sendTyping(false), 900)
  }

  const sendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const content = draft.trim()
    if (!chatId || !content) {
      return
    }
    setError('')
    try {
      await apiFetch(`/api/v1/chats/${chatId}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, attachments: [] }),
      })
      setDraft('')
      sendTyping(false)
      void loadChats()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не вдалося надіслати повідомлення')
    }
  }

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  const toggleLike = async (message: Message) => {
    if (!chatId) {
      return
    }
    try {
      const response = await apiFetch(
        `/api/v1/chats/${chatId}/messages/${message.id}/like?liked=${!message.likedByMe}`,
        { method: 'POST' },
      )
      const updated = (await response.json()) as Message
      setMessages((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не вдалося оновити реакцію')
    }
  }

  const contacts = profile?.role === 'FOREMAN'
    ? colleagues.filter((user) => user.role === 'BUILDER' || user.role === 'SUPPLIER')
    : []

  return (
    <main className="chat-page">
      <header className="chat-header">
        <a className="chat-brand" href="/">BRICKLAYERS<span>TEAM CHAT</span></a>
        <div className="chat-user">
          {profile && (
            <span><strong>{displayName(profile)}</strong> · {roleLabels[profile.role] ?? profile.role}</span>
          )}
          <button type="button" className="users-link link-button" onClick={onLogout}>Вийти</button>
        </div>
      </header>

      <section className="chat-layout">
        <aside className="chat-sidebar">
          <div className="sidebar-heading">
            <div>
              <p className="chat-kicker">РОБОЧИЙ ПРОСТІР</p>
              <h1>Повідомлення</h1>
            </div>
            <span className={`connection-dot ${connection}`} title={connection} />
          </div>

          {chats.map((item) => {
            const title = chatTitle(item)
            return (
              <button
                key={item.id}
                type="button"
                className={item.id === chatId ? 'chat-item active' : 'chat-item'}
                onClick={() => selectChat(item.id)}
              >
                <span className="chat-avatar">{title.slice(0, 1).toUpperCase()}</span>
                <span className="chat-item-copy">
                  <strong>{title}</strong>
                  <small>{item.lastMessagePreview ?? chatTypeLabels[item.type] ?? item.type}</small>
                </span>
                {item.unreadCount > 0 && item.id !== chatId && (
                  <span className="unread-badge">{item.unreadCount}</span>
                )}
              </button>
            )
          })}
          {!loading && !blocked && chats.length === 0 && (
            <p className="sidebar-empty">
              {profile?.role === 'FOREMAN'
                ? 'Чатів ще немає. Почніть розмову з кимось із команди нижче.'
                : 'Чатів ще немає. Прораб може почати з вами розмову.'}
            </p>
          )}

          {contacts.length > 0 && (
            <>
              <p className="sidebar-section-title">НОВИЙ ЧАТ</p>
              {contacts.map((colleague) => (
                <button
                  key={colleague.id}
                  type="button"
                  className="chat-item"
                  onClick={() => void startDirectChat(colleague)}
                >
                  <span className="chat-avatar">{displayName(colleague).slice(0, 1).toUpperCase()}</span>
                  <span className="chat-item-copy">
                    <strong>{displayName(colleague)}</strong>
                    <small>{roleLabels[colleague.role] ?? colleague.role}</small>
                  </span>
                  <span className="chat-arrow">›</span>
                </button>
              ))}
            </>
          )}
        </aside>

        <section className="conversation">
          <div className="conversation-header">
            <div className="chat-avatar large">{chat ? chatTitle(chat).slice(0, 1).toUpperCase() : '·'}</div>
            <div className="conversation-title">
              <h2>{chat ? chatTitle(chat) : 'Оберіть чат'}</h2>
              <span>
                {!chat ? '' : connection === 'connected' ? '● У мережі' : connection === 'connecting' ? 'Підключення…' : 'Не підключено'}
              </span>
            </div>
            {chat && <span className="conversation-type">{chatTypeLabels[chat.type] ?? chat.type}</span>}
          </div>

          {!chat ? (
            <div className="chat-empty">
              <div className="empty-illustration">✳</div>
              <h2>{loading ? 'Завантаження…' : 'Ваш командний чат'}</h2>
              {blocked
                ? <p className="chat-error">{blocked}</p>
                : !loading && <p>Оберіть розмову зліва{profile?.role === 'FOREMAN' ? ' або почніть нову' : ''}.</p>}
              {error && <p className="chat-error">{error}</p>}
            </div>
          ) : (
            <>
              <div className="message-list" aria-live="polite">
                {messages.length === 0 && (
                  <p className="messages-empty">Почніть розмову — надішліть перше повідомлення.</p>
                )}
                {messages.map((message) => {
                  const own = message.senderId === profile?.id
                  return (
                    <article key={message.id} className={`message-row ${own ? 'own' : ''}`}>
                      {!own && <span className="message-avatar">{message.senderName?.slice(0, 1) ?? 'У'}</span>}
                      <div className="message-content">
                        {!own && <span className="message-author">{message.senderName}</span>}
                        <div className={`message-bubble ${message.type === 'SYSTEM' ? 'system' : ''}`}>
                          {message.deleted ? 'Повідомлення видалено' : message.content}
                        </div>
                        <div className="message-meta">
                          <time>{new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
                          {message.type !== 'SYSTEM' && (
                            <button type="button" className="like-button" onClick={() => void toggleLike(message)}>
                              {message.likedByMe ? '♥' : '♡'} {message.likesCount || ''}
                            </button>
                          )}
                        </div>
                      </div>
                    </article>
                  )
                })}
                {typing && <p className="typing-indicator">Співрозмовник друкує…</p>}
              </div>
              {error && <p className="chat-error inline-error">{error}</p>}
              <form className="message-composer" onSubmit={(event) => void sendMessage(event)}>
                <textarea
                  value={draft}
                  onChange={(event) => handleDraftChange(event.target.value)}
                  onKeyDown={handleComposerKeyDown}
                  placeholder="Напишіть повідомлення…"
                  rows={1}
                  aria-label="Текст повідомлення"
                />
                <button type="submit" className="send-button" disabled={!draft.trim()}>
                  Надіслати <span>↗</span>
                </button>
              </form>
              <p className="composer-hint">Enter — надіслати · Shift + Enter — новий рядок</p>
            </>
          )}
        </section>
      </section>
    </main>
  )
}

export default ChatPage
