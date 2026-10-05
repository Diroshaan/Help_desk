import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API, errorMessage, fieldErrors, initials, request, requestForm } from '../api.js'
import { Avatar, Field, Notice, PhoneList, SelectField } from '../components/Bits.jsx'
import { Sidebar } from '../components/Sidebar.jsx'
import { useSession } from '../hooks/useSession.jsx'

const DEPARTMENTS = [
  { value: '', label: 'Not set' },
  { value: 'Faculty of Computing', label: 'Faculty of Computing' },
  { value: 'Faculty of Engineering', label: 'Faculty of Engineering' },
  { value: 'Faculty of Business', label: 'Faculty of Business' },
  { value: 'Faculty of Humanities & Sciences', label: 'Faculty of Humanities & Sciences' },
  { value: 'Other', label: 'Other' }
]

/* Keys must match ProfileUpdateRequest exactly: Spring ignores unknown JSON
   fields, so a wrong name would save nothing without any error. */
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
  const { status, student, setStudent } = useSession()

  const [form, setForm] = useState(null)
  const [prefs, setPrefs] = useState(
    () => Object.fromEntries(PREFERENCES.map(p => [p.key, true]))
  )
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)          // 'profile' | 'prefs' | null

  /* only redirect once we know it's a guest, not while loading */
  useEffect(() => {
    if (status === 'guest') navigate('/', { replace: true })
  }, [status, navigate])

  useEffect(() => {
    if (!student) return
    setForm({
      givenName: student.givenName || '',
      surname: student.surname || '',
      studentId: student.studentId || '',
      // fall back to the single `phone` field for older accounts
      phones: Array.isArray(student.phones) && student.phones.length
        ? student.phones
        : (student.phone ? [student.phone] : ['']),
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

    /* Student ID and email are not editable (the email is the login), so they
   are left out of the payload. */
    /* The whole phone list replaces the old one; an empty list removes them all. */
    const payload = {
      givenName: form.givenName.trim(),
      surname: form.surname.trim(),
      phones: form.phones.map(p => p.trim()).filter(Boolean),
      department: form.department
    }

    try {
      const result = await request(API.student(student.id), { method: 'PUT', body: payload })

      if (result.ok) {
        setStudent(result.data || { ...student, ...payload })
        setNotice({ kind: 'info', text: 'Your profile has been updated.' })
        return
      }

      const fields = fieldErrors(result, ['givenName', 'surname', 'phones', 'department'])
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

  /**
   * Uploads a new profile picture. The input value is cleared so picking the
   * same file again still fires onChange.
   */
  async function uploadAvatar(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setNotice(null); setBusy('avatar')
    try {
      const form = new FormData()
      form.append('file', file)
      const result = await requestForm(API.studentAvatar(student.id), { method: 'POST', form })

      if (result.ok) {
        setStudent(result.data)
        setNotice({ kind: 'info', text: 'Your profile picture has been updated.' })
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not upload that image.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Your picture was not changed.' })
    } finally {
      setBusy(null)
    }
  }

  const marks = initials(student.fullName)
  const activity = Array.isArray(student.activityLog) ? student.activityLog : []

  return (
    <div className="shell">
      <Sidebar />

      <main className="content">
        <div className="content-col rise rise-1">

          <div className="page-head"><h1>My profile</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          {/* Identity */}
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

                <div className="btn-row" style={{ marginTop: 12 }}>
                  <label className="btn btn--ghost">
                    {busy === 'avatar'
                      ? 'Uploading…'
                      : (student.profilePictureUrl ? 'Change picture' : 'Add a picture')}
                    <input type="file" hidden accept="image/jpeg,image/png,image/webp"
                           onChange={uploadAvatar} disabled={busy === 'avatar'} />
                  </label>
                </div>
                {/* accept= is only a convenience; the server checks the real file type. */}
                <p className="hint" style={{ marginTop: 6 }}>JPEG, PNG or WebP, up to 2MB.</p>
              </div>
            </div>
          </section>

          {/* Personal details */}
          <section className="section">
            <h2>Personal details</h2>

            <form className="form" style={{ marginTop: 0 }} onSubmit={saveProfile} noValidate>
              <div className="field-row">
                <Field id="givenName" label="Given name(s)" type="text" autoComplete="given-name"
                       value={form.givenName} onChange={set('givenName')} error={errors.givenName} />

                <Field id="surname" label="Surname" type="text" autoComplete="family-name"
                       value={form.surname} onChange={set('surname')} error={errors.surname} />
              </div>

              <Field id="studentId" label="Student ID" type="text" className="mono" disabled
                     value={form.studentId} onChange={() => {}}
                     hint="Issued by the university. It cannot be changed here." />

              <PhoneList values={form.phones}
                         onChange={phones => setForm(current => ({ ...current, phones }))}
                         error={errors.phones} />

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

          {/* Preferences */}
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

          {/* Activity */}
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
                Nothing here yet. Actions on your account — signing in, profile edits and
                preference changes — appear here as they happen.
              </p>
            )}
          </section>

          {/* Security */}
          <section className="section" id="security">
            <h2>Password</h2>
            <p className="section-note">
              Changing your password signs you out everywhere else you are logged in.
            </p>
            <div className="btn-row">
              <Link className="btn btn--ghost" to="/account/password">Change password</Link>
            </div>
          </section>

          {/* Danger zone */}
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
