import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, errorMessage, fieldErrors, initials, request } from '../../api.js'
import { Avatar, Field, Notice } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'
import { useSession } from '../../hooks/useSession.jsx'

/* Same two switches the student profile shows, backed by the same two
   booleans on Officer. They are read by the Strategy in
   notification/channel (PortalNotificationChannel.isEnabledFor and
   EmailNotificationChannel.isEnabledFor), so turning one off here really does
   stop that channel for this officer - it is not a cosmetic setting. */
const PREFERENCES = [
  {
    key: 'emailNotificationsEnabled',
    title: 'Email me about my queue',
    body: 'Status changes on tickets you are working, and account security messages.'
  },
  {
    key: 'portalNotificationsEnabled',
    title: 'Show notifications in the help desk',
    body: 'The Notifications page in the sidebar, with an unread count.'
  }
]

/**
 * An officer's own profile (F1, US-04): GET/PUT /api/officers/me.
 *
 * WHAT IS EDITABLE AND WHAT IS NOT, AND WHY
 * -----------------------------------------
 * Editable: display name, contact number, notification preferences - things
 * that are the officer's own business.
 *
 * Read-only: staff number, job title and departments. Those are set by an
 * administrator when the account is provisioned (F6), because they decide
 * what the officer is ALLOWED to do - which department queues they see.
 * Letting an officer add themselves to a department would be a privilege
 * escalation, so OfficerProfileUpdateRequest does not even have those fields;
 * this screen shows them so the officer can see what they have been given.
 *
 * Email is the login identity and is shown locked, as on the student profile.
 */
export default function OfficerProfile() {
  // refresh() re-reads /api/auth/me, so a renamed officer sees the new name in
  // the sidebar straight away instead of after the next sign-in.
  const { refresh } = useSession()
  const [profile, setProfile] = useState(null)
  const [form, setForm] = useState(null)
  const [prefs, setPrefs] = useState(null)
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)       // 'profile' | 'prefs' | null
  const [loadError, setLoadError] = useState('')

  function fill(data) {
    setProfile(data)
    setForm({ fullName: data.fullName || '', phone: data.phone || '' })
    setPrefs(Object.fromEntries(PREFERENCES.map(p => [p.key, data[p.key] ?? true])))
  }

  useEffect(() => {
    request(API.officerMe).then(result => {
      if (result.ok) fill(result.data)
      else setLoadError(errorMessage(result, 'We could not load your profile.'))
    }).catch(() => setLoadError('Could not reach the server.'))
  }, [])

  async function save(body, which, successText) {
    setErrors({}); setNotice(null); setBusy(which)
    try {
      const result = await request(API.officerMe, { method: 'PUT', body })
      if (result.ok) {
        fill(result.data)
        setNotice({ kind: 'info', text: successText })
        if (which === 'profile') refresh()
        return
      }
      const fields = fieldErrors(result, ['fullName', 'phone'])
      if (Object.keys(fields).length) setErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save those changes.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Nothing was saved.' })
    } finally {
      setBusy(null)
    }
  }

  function saveProfile(event) {
    event.preventDefault()
    // `phone` is the JSON name of OfficerProfileUpdateRequest.contactNumber
    // (@JsonProperty("phone")), the same name the student side uses.
    save({ fullName: form.fullName.trim(), phone: form.phone.trim() },
         'profile', 'Your profile has been updated.')
  }

  if (loadError) {
    return <div className="shell"><Sidebar /><main className="content"><div className="content-col">
      <Notice kind="error">{loadError}</Notice>
    </div></main></div>
  }

  if (!profile) {
    return <div className="shell"><Sidebar /><main className="content"><div className="content-col">
      <p className="empty">Loading your profile…</p>
    </div></main></div>
  }

  const departments = Array.isArray(profile.departments) ? profile.departments : []

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>My profile</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <div className="identity">
              <Avatar marks={initials(profile.fullName)} />
              <div>
                <p className="identity__name">{profile.fullName || '(no name set)'}</p>
                <p className="identity__meta">
                  <span className="mono">{profile.staffNumber || ''}</span>
                  {profile.jobTitle ? ' · ' + profile.jobTitle : ''}
                </p>
              </div>
            </div>
          </section>

          <section className="section">
            <h2>Your details</h2>
            <form className="form" style={{ marginTop: 0 }} onSubmit={saveProfile} noValidate>
              <Field id="fullName" label="Display name" type="text" autoComplete="name"
                     value={form.fullName} onChange={e => setForm(f => ({ ...f, fullName: e.target.value }))}
                     error={errors.fullName}
                     hint="Students see this name on the answers you post." />

              <Field id="phone" label="Contact number" type="tel" autoComplete="tel"
                     value={form.phone} onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
                     error={errors.phone} />

              <Field id="email" label="Email" type="email" disabled value={profile.email || ''}
                     onChange={() => {}} hint="Your login identity. An administrator can change it." />

              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'profile'}>
                  {busy === 'profile' ? 'Saving…' : 'Save changes'}
                </button>
              </div>
            </form>
          </section>

          <section className="section">
            <h2>Assigned by your administrator</h2>
            <dl className="detail-list">
              <div><dt>Staff number</dt><dd className="mono">{profile.staffNumber || '—'}</dd></div>
              <div><dt>Job title</dt><dd>{profile.jobTitle || '—'}</dd></div>
              <div>
                <dt>Departments</dt>
                <dd>
                  {departments.length
                    ? departments.map(d => d.name + ' (' + d.code + ')').join(', ')
                    : 'None yet. Ask an administrator to add you to a department, or its queue stays empty for you.'}
                </dd>
              </div>
            </dl>
          </section>

          <section className="section" id="preferences">
            <h2>Notification preferences</h2>
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
              <button type="button" className="btn btn--primary" disabled={busy === 'prefs'}
                      onClick={() => save(prefs, 'prefs', 'Your notification preferences have been saved.')}>
                {busy === 'prefs' ? 'Saving…' : 'Save preferences'}
              </button>
            </div>
          </section>

          <section className="section">
            <h2>Password</h2>
            <p className="section-note">
              If an administrator set your first password, change it to one only you know.
            </p>
            <div className="btn-row">
              <Link className="btn btn--ghost" to="/account/password">Change password</Link>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
