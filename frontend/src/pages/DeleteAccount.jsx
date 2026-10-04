import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API, errorMessage, request } from '../api.js'
import { BrandPanel, Field, Notice } from '../components/Bits.jsx'
import { useSession } from '../hooks/useSession.jsx'

const POINTS = [
  'Requests you have already raised stay with the departments that handled them.',
  'Your saved folders and bookmarks are removed.',
  'You will not be able to log in again with this Student ID.'
]

export default function DeleteAccount() {
  const navigate = useNavigate()
  const { status, student, signOut } = useSession()

  const [typedId, setTypedId] = useState('')
  const [password, setPassword] = useState('')
  const [passwordError, setPasswordError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (status === 'guest' && !done) navigate('/', { replace: true })
  }, [status, done, navigate])

  /* After a delete, student becomes null, so the `done` screen must be checked
     before the loading guard below or the page shows "Loading…" forever. */
  useEffect(() => {
    if (!done) return

    // show the confirmation for five seconds, then go home
    const timer = setTimeout(() => navigate('/', { replace: true }), 5000)
    // signOut() waits until we leave this page; doing it straight away made the
    // route guard replace the confirmation with the login form.
    return () => { clearTimeout(timer); signOut() }
  }, [done, navigate, signOut])

  if (done) {
    return (
      <div className="split">
        <BrandPanel home="/" heading="Your account is closed." points={POINTS} />

        <main className="form-side">
          <div className="form-col rise rise-2">
            <h1>Account deleted</h1>
            <Notice style={{ marginTop: 22 }}>
              <strong>Your account has been deleted and you are signed out.</strong><br />
              Thank you for using the help desk.
            </Notice>

            <div className="btn-row" style={{ marginTop: 24 }}>
              <Link className="btn btn--primary" to="/">Back to the help desk</Link>
            </div>

            <p className="hint" style={{ marginTop: 14 }}>
              Taking you back to the help desk in a moment…
            </p>
          </div>
        </main>
      </div>
    )
  }

  if (status === 'loading' || !student) {
    return (
      <div className="split">
        <BrandPanel home="/profile" heading="Before you close your account." points={POINTS} />
        <main className="form-side">
          <div className="form-col"><p className="empty">Loading…</p></div>
        </main>
      </div>
    )
  }

  /* Delete stays disabled until the typed Student ID matches this account. */
  const matches = typedId.trim() === student.studentId

  async function handleSubmit(event) {
    event.preventDefault()
    setPasswordError(''); setNotice(null)

    if (!password) {
      setPasswordError('Enter your password to confirm.')
      return
    }

    setBusy(true)

    try {
      const result = await request(API.student(student.id), { method: 'DELETE' })

      if (result.ok || result.status === 204) {
        // end the server session now; the browser side is cleared on leaving
        await request(API.logout, { method: 'POST' }).catch(() => {})
        setDone(true)
        return
      }

      setNotice({ kind: 'error', text: errorMessage(result, 'We could not delete the account.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. The account was not deleted.' })
    } finally {
      if (!done) setBusy(false)
    }
  }

  return (
    <div className="split">
      <BrandPanel home="/profile" heading="Before you close your account." points={POINTS} />

      <main className="form-side">
        <div className="form-col rise rise-2">
          <Link className="back-link" to="/profile">&larr; Back to my profile</Link>

          <h1>Delete my account</h1>
          <p className="lede">This cannot be undone. Please read the panel on the left first.</p>

          {notice && <Notice kind={notice.kind} style={{ marginTop: 22 }}>{notice.text}</Notice>}

          <div className="detail-list" style={{ marginTop: 26 }}>
            <div><dt>Account</dt><dd>{student.fullName || '(no name set)'}</dd></div>
            <div><dt>Student ID</dt><dd className="mono">{student.studentId || ''}</dd></div>
            <div><dt>Email</dt><dd>{student.email || ''}</dd></div>
          </div>

          <form className="form" onSubmit={handleSubmit} noValidate>
            <Field id="confirmId" label="Type your Student ID to confirm" type="text"
                   className="mono" autoComplete="off" placeholder="ITxxxxxxxx"
                   value={typedId} onChange={e => setTypedId(e.target.value)} />

            <Field id="password" label="Your password" type="password"
                   autoComplete="current-password" error={passwordError}
                   value={password} onChange={e => setPassword(e.target.value)} />

            <div className="btn-row">
              <button type="submit" className="btn btn--danger" disabled={!matches || busy}>
                {busy ? 'Deleting…' : 'Delete my account'}
              </button>
              <Link className="btn btn--ghost" to="/profile">Cancel</Link>
            </div>
          </form>
        </div>
      </main>
    </div>
  )
}
