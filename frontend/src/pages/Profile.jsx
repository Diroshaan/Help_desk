import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API, errorMessage, fieldErrors, initials, request } from '../api.js'
import { Avatar, Field, Notice, SelectField } from '../components/Bits.jsx'
import { useSession } from '../hooks/useSession.jsx'

const DEPARTMENTS = [
  { value: '', label: 'Not set' },
  { value: 'Faculty of Computing', label: 'Faculty of Computing' },
  { value: 'Faculty of Engineering', label: 'Faculty of Engineering' },
  { value: 'Faculty of Business', label: 'Faculty of Business' },
  { value: 'Faculty of Humanities & Sciences', label: 'Faculty of Humanities & Sciences' },
  { value: 'Other', label: 'Other' }
]

/* These keys must match the field names on ProfileUpdateRequest exactly.
   Spring Boot ignores JSON properties a DTO does not declare, so a wrong name
   here does not fail — the PUT returns 200 and silently saves nothing, which is
   worse than an error. That is exactly what happened when this array used
   invented names. */
const PREFERENCES = [
  {
    key: 'emailNotificationsEnabled',
    title: 'Email me about my requests',
    body: 'Replies from a support officer, and every status change — Open, In progress, Resolved or Closed.'
  },
  {
    key: 'portalNotificationsEnabled',
    title: 'Show updates in the help desk',
    body: 'Notifications appear when you sign in, whether or not email is switched on.'
  }
]

export default function Profile() {
  const navigate = useNavigate()
  const { status, student, setStudent, signOut } = useSession()

  const [form, setForm] = useState(null)
  // Built from PREFERENCES so adding a preference means editing one array.
  const [prefs, setPrefs] = useState(
    () => Object.fromEntries(PREFERENCES.map(p => [p.key, true]))
  )
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)          // 'profile' | 'prefs' | null

  /* A guest has no profile to show. Waiting for 'guest' rather than acting on
     a missing student is the whole point of the three-valued status — during
     'loading' the answer is simply not known yet. */
  useEffect(() => {
    if (status === 'guest') navigate('/', { replace: true })
  }, [status, navigate])

  // Fill the form once the account arrives, and again whenever it changes.
  useEffect(() => {
    if (!student) return
    setForm({
      fullName: student.fullName || '',
      studentId: student.studentId || '',
      phone: student.phone || '',
      email: student.email || '',
      department: student.department || ''
    })
    setPrefs(Object.fromEntries(
      PREFERENCES.map(p => [p.key, student[p.key] ?? true])
    ))
  }, [student])

  if (status === 'loading' || !form) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col"><p className="empty">Loading your profile…</p></div>
    </main></div>
  }

  function set(name) {
    return event => setForm(current => ({ ...current, [name]: event.target.value }))
  }

  async function saveProfile(event) {
    event.preventDefault()
    setErrors({}); setNotice(null); setBusy('profile')

    /* Student ID and email are both deliberately absent from this payload.
       The Student ID is issued by the university. The email doubles as the login
       identity and is what the ownership check compares against, so
       ProfileUpdateRequest excludes it by design — sending it would be ignored,
       and showing an editable box that silently does nothing is worse than
       showing a locked one. */
    const payload = {
      fullName: form.fullName.trim(),
      phone: form.phone.trim(),
      department: form.department
    }

    try {
      const result = await request(API.student(student.id), { method: 'PUT', body: payload })

      if (result.ok) {
        setStudent(result.data || { ...student, ...payload })
        setNotice({ kind: 'info', text: 'Your profile has been updated.' })
        return
      }

      const fields = fieldErrors(result, ['fullName', 'phone', 'department'])
      if (Object.keys(fields).length) setErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save those changes.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Your changes were not saved.' })
    } finally {
      setBusy(null)
    }
  }

  async function savePrefs() {
    setNotice(null); setBusy('prefs')

    try {
      const result = await request(API.student(student.id), { method: 'PUT', body: prefs })

      if (result.ok) {
        if (result.data) setStudent(result.data)
        setNotice({ kind: 'info', text: 'Your notification preferences have been saved.' })
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not save your preferences.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Your preferences were not saved.' })
    } finally {
      setBusy(null)
    }
  }

  const marks = initials(student.fullName)
  const activity = Array.isArray(student.activityLog) ? student.activityLog : []

  return (
    <div className="shell">
      <Sidebar student={student} onSignOut={async () => { await signOut(); navigate('/') }}
               onTicket={() => {
                 setNotice({
                   kind: 'info',
                   text: 'Ticket submission is part of F2 and arrives in Sprint 3. Until then, the department contacts are listed at the bottom of the help desk home page.'
                 })
                 window.scrollTo({ top: 0, behavior: 'smooth' })
               }} />

      <main className="content">
        <div className="content-col rise rise-1">

          <div className="page-head"><h1>My profile</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          {/* ---------- Identity ---------- */}
          <section className="section">
            <div className="identity">
              <Avatar marks={marks} src={student.profilePictureUrl} />
              <div>
                <p className="identity__name">{student.fullName || '(no name set)'}</p>
                <p className="identity__meta">
                  <span className="mono">{student.studentId || ''}</span>
                  {' · '}
                  <span className={'pill' + (student.active === false ? ' pill--muted' : '')}>
                    {student.active === false ? 'Suspended' : 'Active'}
                  </span>
                </p>
              </div>
            </div>
          </section>

          {/* ---------- Personal details ---------- */}
          <section className="section">
            <h2>Personal details</h2>

            <form className="form" style={{ marginTop: 0 }} onSubmit={saveProfile} noValidate>
              <Field id="fullName" label="Full name" type="text" autoComplete="name"
                     value={form.fullName} onChange={set('fullName')} error={errors.fullName} />

              <div className="field-row">
                <Field id="studentId" label="Student ID" type="text" className="mono" disabled
                       value={form.studentId} onChange={() => {}}
                       hint="Issued by the university. It cannot be changed here." />

                <Field id="phone" label="Phone" type="tel" autoComplete="tel"
                       value={form.phone} onChange={set('phone')} error={errors.phone} />
              </div>

              <Field id="email" label="University email" type="email" disabled
                     value={form.email} onChange={() => {}}
                     hint="This is your login identity. Contact the help desk administrator to change it." />

              <SelectField id="department" label="Faculty or department" options={DEPARTMENTS}
                           value={form.department} onChange={set('department')} error={errors.department} />

              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'profile'}>
                  {busy === 'profile' ? 'Saving…' : 'Save changes'}
                </button>
                <button type="button" className="btn btn--ghost"
                        onClick={() => { setErrors({}); setNotice(null); setStudent({ ...student }) }}>
                  Discard
                </button>
              </div>
            </form>
          </section>

          {/* ---------- Preferences ---------- */}
          <section className="section" id="preferences">
            <h2>Notification preferences</h2>
            <p className="section-note">
              Choose when the help desk should email you. These apply to every request you raise.
            </p>

            {PREFERENCES.map(pref => (
              <div className="pref" key={pref.key}>
                <div className="pref__text">
                  <strong>{pref.title}</strong>
                  <span>{pref.body}</span>
                </div>
                <label className="switch">
                  <span className="sr-only">{pref.title}</span>
                  <input type="checkbox" checked={prefs[pref.key]}
                         onChange={e => setPrefs(c => ({ ...c, [pref.key]: e.target.checked }))} />
                </label>
              </div>
            ))}

            <div className="btn-row" style={{ marginTop: 22 }}>
              <button type="button" className="btn btn--primary"
                      onClick={savePrefs} disabled={busy === 'prefs'}>
                {busy === 'prefs' ? 'Saving…' : 'Save preferences'}
              </button>
            </div>
          </section>

          {/* ---------- Activity ---------- */}
          <section className="section" id="activity">
            <h2>Recent activity</h2>

            {activity.length ? (
              <ul className="timeline">
                {activity.map((entry, index) => (
                  <li key={index}>
                    <time>{entry.timestamp || ''}</time>
                    <p>{entry.description || ''}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="empty">
                Nothing here yet. Actions on your account — profile edits, password changes
                and logins — will appear here once activity logging is switched on.
              </p>
            )}
          </section>

          {/* ---------- Danger zone ---------- */}
          <section className="danger-zone">
            <h2>Close your account</h2>
            <p className="section-note">
              Your requests stay on record for the departments that handled them,
              but you will no longer be able to log in.
            </p>
            <Link className="danger-link" to="/delete-account">Delete my account</Link>
          </section>

        </div>
      </main>
    </div>
  )
}

/* --------------------------------------------------------------------------
   The sidebar. Two groups: out to the help desk itself, and into the account.
   Without the first group the profile page is a dead end — the only way back
   to the knowledge base was editing the address bar.
   -------------------------------------------------------------------------- */
function Sidebar({ student, onSignOut, onTicket }) {
  return (
    <aside className="sidebar">
      <Link className="brand" to="/">UNIHELP</Link>

      <nav className="side-nav">
        <p className="side-nav__label">Help desk</p>
        <Link to="/">Home</Link>
        <Link to="/#topics">Browse FAQ</Link>
        <a href="#" onClick={event => { event.preventDefault(); onTicket?.() }}>Submit a ticket</a>

        <p className="side-nav__label">My account</p>
        <Link to="/profile" aria-current="page">My profile</Link>
        <a href="#preferences">Preferences</a>
        <a href="#activity">Recent activity</a>
      </nav>

      <div className="side-foot">
        <p className="side-user">
          <span>{(student?.fullName || '').split(' ')[0] || 'Student'}</span>
          <span className="mono">{student?.studentId || ''}</span>
        </p>
        <button className="side-logout" type="button" onClick={onSignOut}>Log out</button>
      </div>
    </aside>
  )
}
