import { useEffect, useState } from 'react'
import { API, formatDateTime, request } from '../api.js'
import { Sidebar } from '../components/Sidebar.jsx'

/**
 * Announcements for any signed-in role. The server already filters them by the
 * user's role, so we don't filter again here; visibleToRoles is only displayed.
 */
export default function Announcements() {
  const [announcements, setAnnouncements] = useState([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    request(API.announcements).then(r => {
      if (r.ok) setAnnouncements(r.data || [])
      else setFailed(true)
      setLoading(false)
    })
  }, [])

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Announcements</h1></div>

          <p className="section-note">
            Notices from the help desk. Only announcements meant for your role
            are listed.
          </p>

          <section className="section">
            {loading && <p className="empty">Loading announcements…</p>}

            {!loading && failed && (
              <p className="empty">We could not load announcements just now.</p>
            )}

            {!loading && !failed && announcements.length === 0 && (
              <p className="empty">There are no announcements at the moment.</p>
            )}

            {!loading && announcements.map(announcement => (
              <article key={announcement.id} className="section" style={{ paddingTop: 0 }}>
                <h2 style={{ marginBottom: 4 }}>{announcement.title}</h2>

                <p className="foot-note" style={{ marginTop: 0 }}>
                  {formatDateTime(announcement.publishedAt)}
                  {announcement.publishedByName ? ' · ' + announcement.publishedByName : ''}
                  {/* only future expiry dates are shown */}
                  {announcement.expiresAt && !announcement.expired
                    ? ' · until ' + formatDateTime(announcement.expiresAt)
                    : ''}
                </p>

                {announcement.visibleToRoles?.length > 0 && (
                  <div className="chips" style={{ marginTop: 8 }}>
                    {announcement.visibleToRoles.map(role => (
                      <span className="chip" key={role}>
                        {role.charAt(0) + role.slice(1).toLowerCase()}
                      </span>
                    ))}
                  </div>
                )}

                <p style={{ whiteSpace: 'pre-wrap', marginTop: 10 }}>{announcement.body}</p>
              </article>
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
