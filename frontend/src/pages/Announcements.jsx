import { useEffect, useState } from 'react'
import { API, formatDateTime, request } from '../api.js'
import { Sidebar } from '../components/Sidebar.jsx'

/**
 * F6 - the announcements a signed-in user is allowed to see.
 *
 * WHY THIS SCREEN HAD TO EXIST
 * ----------------------------
 * GET /api/announcements has been on the backend since F6 merged, and nothing
 * called it. Administrators could publish announcements and then nobody -
 * student, officer or administrator - had any way to read one. The feature was
 * write-only: half a system, and the half that was missing is the half that
 * serves the people it was written for.
 *
 * WHY THERE IS NO ROLE FILTER IN THIS FILE
 * ----------------------------------------
 * An announcement carries visibleToRoles, and it is tempting to filter on it
 * here. That would be the wrong layer, and it would be wrong in the dangerous
 * direction: AnnouncementService already filters by the caller's role before
 * the list ever leaves the server, so anything that arrives here is something
 * this user is permitted to see. Filtering again in the browser would only
 * ever HIDE a permitted announcement while providing no protection at all -
 * anyone can read the JSON the page received. Server decides who may see what;
 * this file decides how it looks.
 *
 * visibleToRoles is still DISPLAYED, because "Students only" is useful context
 * for an officer reading a notice that students can also see.
 *
 * Available to every signed-in role rather than students alone: an officer or
 * an administrator needs the same operational notices - a system outage, a
 * closure - as everybody else.
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
                  {/* An expiry in the future is useful ("this notice stands
                      until Friday"); one in the past is not shown, because the
                      server does not return expired announcements in the first
                      place and claiming an expiry date has passed on a notice
                      you can still read is contradictory. */}
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
