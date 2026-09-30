import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { API, errorMessage, formatDateTime, request } from '../api.js'
import { Notice } from '../components/Bits.jsx'
import { Sidebar } from '../components/Sidebar.jsx'
import { announceUnreadChanged } from '../hooks/useUnreadCount.js'

/**
 * The notification inbox - every signed-in role.
 *
 * This is the visible end of the two design patterns in notification/:
 *   Observer  - QueueService and PasswordService publish events; the
 *               listeners (TicketStatusNotifier, AccountSecurityNotifier)
 *               turn them into messages without the publishers knowing.
 *   Strategy  - NotificationService hands each message to every enabled
 *               NotificationChannel. PortalNotificationChannel is the one that
 *               writes the rows this page reads.
 * So this page only ever READS and MARKS. It never creates a notification.
 *
 * Clicking an item marks it read first, then follows its link. The link is
 * stored as a hash route ("#/tickets/42") because the app uses HashRouter;
 * the leading '#' is stripped before navigating. An item with no link (the
 * password notice) is only marked read.
 *
 * The server keeps the newest 50 (findTop50...), which is plenty for a help
 * desk inbox and keeps this page one fast query.
 */
export default function Notifications() {
  const navigate = useNavigate()
  const [items, setItems] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  async function load() {
    try {
      const result = await request(API.notifications)
      if (result.ok) setItems(Array.isArray(result.data) ? result.data : [])
      else { setItems([]); setNotice({ kind: 'error', text: errorMessage(result, 'We could not load your notifications.') }) }
    } catch {
      setItems([]); setNotice({ kind: 'error', text: 'Could not reach the server.' })
    }
  }

  useEffect(() => { load() }, [])

  async function open(item) {
    if (!item.read) {
      // Optimistic: show it read straight away. If the server refuses, the
      // next load shows the truth; nothing is lost by a wrong guess here.
      setItems(list => list.map(n => n.id === item.id ? { ...n, read: true } : n))
      try {
        await request(API.notificationRead(item.id), { method: 'POST' })
      } finally {
        announceUnreadChanged()
      }
    }
    if (item.link) navigate(item.link.replace(/^#/, ''))
  }

  async function markAll() {
    setBusy(true); setNotice(null)
    try {
      const result = await request(API.notificationsReadAll, { method: 'POST' })
      if (result.ok) {
        setItems(list => list.map(n => ({ ...n, read: true })))
        announceUnreadChanged()
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not mark them as read.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(false)
    }
  }

  const unread = items ? items.filter(n => !n.read).length : 0

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head row-between">
            <h1>Notifications</h1>
            {unread > 0 && (
              <button type="button" className="btn btn--ghost" onClick={markAll} disabled={busy}>
                {busy ? 'Marking…' : 'Mark all as read'}
              </button>
            )}
          </div>

          {notice && <Notice kind={notice.kind} style={{ marginTop: 18 }}>{notice.text}</Notice>}

          <section className="section">
            {items === null && <p className="empty">Loading…</p>}

            {items && items.length === 0 && !notice && (
              <p className="empty">
                Nothing yet. You will hear here when a ticket you raised changes status,
                or when your password is changed.
              </p>
            )}

            {items && items.map(item => (
              <button type="button" key={item.id} onClick={() => open(item)}
                      className="pref row-link"
                      style={{ width: '100%', textAlign: 'left', background: 'none', border: 0,
                               borderBottom: '1px solid var(--line-soft)', cursor: 'pointer',
                               font: 'inherit', color: 'inherit' }}
                      aria-label={(item.read ? '' : 'Unread: ') + item.title}>
                <span className="pref__text">
                  <strong style={{ fontWeight: item.read ? 400 : 600 }}>{item.title}</strong>
                  <span>{item.body}</span>
                </span>
                <span className="row-side">
                  <span className="hint">{formatDateTime(item.createdAt)}</span>
                  {!item.read && <span className="pill">New</span>}
                </span>
              </button>
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
