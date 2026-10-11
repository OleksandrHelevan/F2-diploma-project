import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { FormEvent, KeyboardEvent } from 'react'
import { Client } from '@stomp/stompjs'
import './ChatDemo.css'

type DemoRole = 'FOREMAN' | 'BUILDER'

type Chat = {
  id: string
  name: string
  type: string
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

const roleUsers: Record<DemoRole, { id: string; name: string; label: string }> = {
  FOREMAN: { id: 'demo-foreman', name: 'Олексій Прораб', label: 'Прораб' },
  BUILDER: { id: 'demo-builder', name: 'Іван Будівельник', label: 'Будівельник' },
}

function initialRole(): DemoRole {
  return sessionStorage.getItem('chat-demo-role') === 'BUILDER' ? 'BUILDER' : 'FOREMAN'
}

function encodeHeaderValue(value: string): string {
  const bytes = new TextEncoder().encode(value)
  return btoa(Array.from(bytes, (byte) => String.fromCharCode(byte)).join(''))
}

function ChatDemo() {
  const [role, setRole] = useState<DemoRole>(initialRole)
  const [chat, setChat] = useState<Chat | null>(null)
  const [chats, setChats] = useState<Chat[]>([])
  const [messages, setMessages] = useState<Message[]>([])
  const [draft, setDraft] = useState('')
  const [connection, setConnection] = useState<'connecting' | 'connected' | 'disconnected'>('disconnected')
  const [typingName, setTypingName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const clientRef = useRef<Client | null>(null)
  const typingTimerRef = useRef<number | undefined>(undefined)
  const currentUser = roleUsers[role]

  const identityHeaders = useMemo(() => ({
    'x-user-id': currentUser.id,
    'x-user-name-base64': encodeHeaderValue(currentUser.name),
    'x-company-id': 'demo-company',
    'x-user-role': role,
  }), [currentUser.id, currentUser.name, role])

  const request = useCallback(async (path: string, init: RequestInit = {}) => {
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    Object.entries(identityHeaders).forEach(([name, value]) => headers.set(name, value))
    const response = await fetch(path, {
      ...init,
      headers,
    })
    if (!response.ok) {
      const detail = await response.text()
      throw new Error(detail || `Запит завершився з кодом ${response.status}`)
    }
    return response
  }, [identityHeaders])

  const openDemoChat = async () => {
    setLoading(true)
    setError('')
    setMessages([])
    setTypingName('')
    setConnection('connecting')
    try {
      const created = await request('/api/v1/chats/demo', { method: 'POST' })
      const demoChat = (await created.json()) as Chat
      const response = await request('/api/v1/chats?page=0&size=50')
      const page = (await response.json()) as Page<Chat>
      setChats(page.content)
      setChat(page.content.find((item) => item.id === demoChat.id) ?? demoChat)
    } catch (cause) {
      setConnection('disconnected')
      setError(cause instanceof Error ? cause.message : 'Не вдалося відкрити чат')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    const chatId = chat?.id
    if (!chatId) {
      return
    }

    let active = true

    void request(`/api/v1/chats/${chatId}/messages?page=0`)
      .then(async (response) => {
        const page = (await response.json()) as { messages: Message[] }
        if (active) {
          setMessages([...page.messages].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp)))
          await request(`/api/v1/chats/${chatId}/read`, { method: 'POST' })
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
      connectHeaders: identityHeaders,
      reconnectDelay: 3000,
      onConnect: () => {
        if (!active) {
          return
        }
        setConnection('connected')
        client.subscribe(`/topic/chats/${chatId}`, (frame) => {
          const event = JSON.parse(frame.body) as ChatEvent
          if (event.type === 'NEW_MESSAGE' || event.type === 'MESSAGE_EDITED') {
            const message = event.payload as Message
            setMessages((current) => {
              const next = current.filter((item) => item.id !== message.id)
              return [...next, message].sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp))
            })
          } else if (event.type === 'MESSAGE_DELETED' && typeof event.payload === 'string') {
            setMessages((current) => current.map((item) => (
              item.id === event.payload ? { ...item, deleted: true, content: null } : item
            )))
          } else if (event.type === 'LIKE_UPDATE' && typeof event.payload === 'object' && event.payload !== null) {
            const update = event.payload as { messageId: string; likesCount: number }
            setMessages((current) => current.map((item) => (
              item.id === update.messageId ? { ...item, likesCount: update.likesCount } : item
            )))
          } else if (event.type === 'TYPING' && event.userId !== currentUser.id) {
            setTypingName('Співрозмовник друкує…')
          } else if (event.type === 'STOPPED_TYPING' && event.userId !== currentUser.id) {
            setTypingName('')
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
  }, [chat?.id, currentUser.id, identityHeaders, request, role])

  const selectRole = (nextRole: DemoRole) => {
    sessionStorage.setItem('chat-demo-role', nextRole)
    setRole(nextRole)
    setChat(null)
    setMessages([])
    setChats([])
    setConnection('disconnected')
    setError('')
  }

  const sendTyping = (typing: boolean) => {
    if (!chat || clientRef.current?.connected !== true) {
      return
    }
    clientRef.current.publish({
      destination: `/app/chats/${chat.id}/typing`,
      body: JSON.stringify({ typing }),
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
    if (!chat || !content) {
      return
    }
    setError('')
    try {
      await request(`/api/v1/chats/${chat.id}/messages`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ content, attachments: [] }),
      })
      setDraft('')
      sendTyping(false)
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
    if (!chat) {
      return
    }
    try {
      const response = await request(
        `/api/v1/chats/${chat.id}/messages/${message.id}/like?liked=${!message.likedByMe}`,
        { method: 'POST' },
      )
      const updated = (await response.json()) as Message
      setMessages((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Не вдалося оновити реакцію')
    }
  }

  return (
    <main className="chat-demo">
      <header className="chat-header">
        <a className="chat-brand" href="/">BRICKLAYERS<span>TEAM CHAT</span></a>
        <div className="chat-role-picker" aria-label="Оберіть роль">
          {(['FOREMAN', 'BUILDER'] as const).map((nextRole) => (
            <button
              key={nextRole}
              type="button"
              className={role === nextRole ? 'role-button selected' : 'role-button'}
              onClick={() => selectRole(nextRole)}
            >
              {roleUsers[nextRole].label}
            </button>
          ))}
        </div>
        <a className="users-link" href="/users">Керування користувачами</a>
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
          <button className="demo-chat-item active" type="button" onClick={() => void openDemoChat()} disabled={loading}>
            <span className="chat-avatar">Б</span>
            <span className="demo-chat-copy">
              <strong>Демо-бригада</strong>
              <small>{chats.length ? 'Прораб · Будівельник' : 'Натисніть, щоб підключитися'}</small>
            </span>
            <span className="chat-arrow">›</span>
          </button>
          <div className="sidebar-note">
            <span className="note-icon">↗</span>
            <p>Відкрийте сторінку в іншому браузері та виберіть іншу роль, щоб перевірити обмін повідомленнями наживо.</p>
          </div>
        </aside>

        <section className="conversation">
          <div className="conversation-header">
            <div className="chat-avatar large">Б</div>
            <div className="conversation-title">
              <h2>{chat?.name ?? 'Демо-бригада'}</h2>
              <span>{connection === 'connected' ? '● У мережі' : connection === 'connecting' ? 'Підключення…' : 'Не підключено'}</span>
            </div>
            <span className="conversation-type">БРИГАДА</span>
          </div>

          {!chat ? (
            <div className="chat-empty">
              <div className="empty-illustration">✳</div>
              <h2>Ваш командний чат</h2>
              <p>Підключіться як {roleUsers[role].label.toLowerCase()}, а в іншому браузері — під іншою роллю.</p>
              <button type="button" className="send-button" onClick={() => void openDemoChat()} disabled={loading}>
                {loading ? 'Підключення…' : 'Відкрити демо-чат'}
              </button>
              {error && <p className="chat-error">{error}</p>}
            </div>
          ) : (
            <>
              <div className="message-list" aria-live="polite">
                <div className="date-divider"><span>ПОВІДОМЛЕННЯ БРИГАДИ</span></div>
                {messages.length === 0 && (
                  <p className="messages-empty">Почніть розмову — надішліть перше повідомлення.</p>
                )}
                {messages.map((message) => {
                  const own = message.senderId === currentUser.id
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
                {typingName && <p className="typing-indicator">{typingName}</p>}
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
      <footer className="chat-footer">Локальне демо · роль «{roleUsers[role].label}» · {currentUser.name}</footer>
    </main>
  )
}

export default ChatDemo
