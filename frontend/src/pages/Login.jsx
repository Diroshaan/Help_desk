import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { API, errorMessage, request } from '../api.js'
import { BrandPanel, Field, Notice } from '../components/Bits.jsx'
import { useSession } from '../hooks/useSession.jsx'
import { afterLogin, nextFrom } from '../routes.jsx'

const POINTS = [
  'Tickets routed automatically to the department that owns them.',
  'Every response, status change and resolution in one thread.',
  'Save articles and tickets into your own folders.'
]

export default function Login() {
  const navigate = useNavigate()
  const location = useLocation()
  const { refresh } = useSession()
  const next = nextFrom(location.search)

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)      // { kind, text }

  // ?expired=1 is added when the session ran out, so we can explain the redirect
  const [searchParams] = useSearchParams()
  const expired = searchParams.get('expired') === '1'

  /* Coming from Register: prefill the new email once (it is removed after
     reading so a refresh doesn't show the message again). */
  useEffect(() => {
    let justRegistered = null

    try {
      justRegistered = sessionStorage.getItem('unihelp.justRegistered')
      sessionStorage.removeItem('unihelp.justRegistered')
    } catch {
      return      // storage unavailable
    }

    if (!justRegistered) return

    setUsername(justRegistered)
    setNotice({
      kind: 'info',
      text: 'Account created successfully. Log in with the password you just chose.'
    })
    document.getElementById('password')?.focus()
  }, [])

  async function handleSubmit(event) {
    event.preventDefault()
    setNotice(null)

    if (!username.trim() || !password) {
      setNotice({ kind: 'error', text: 'Enter both your Student ID (or email) and your password.' })
      return
    }

    setBusy(true)

    try {
      /* LoginRequest reads `email`; the extra `username` key is ignored. */
      const result = await request(API.login, {
        method: 'POST',
        body: { username: username.trim(), email: username.trim(), password }
      })

      if (result.ok) {
        // load the session first so the next page doesn't flash the guest view
        const me = await refresh()
        navigate(afterLogin(me?.role, location.search), { replace: true })
        return
      }

      /* 403 here means the account is suspended, not a wrong password. */
      if (result.status === 401 || result.status === 400) {
        setNotice({ kind: 'error', text: errorMessage(result, 'That Student ID or password is not correct.') })
      } else if (result.status === 403) {
        setNotice({ kind: 'error', text: errorMessage(result, 'This account has been suspended. Contact the help desk administrator.') })
      } else {
        setNotice({ kind: 'error', text: errorMessage(result) })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Check that the application is running.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="split">
      <BrandPanel heading="One account. Every department." points={POINTS} />

      <main className="form-side">
        <div className="form-col rise rise-2">
          <Link className="back-link" to="/">&larr; Back to help desk</Link>

          <h1>Log in</h1>
          <p className="lede">
            {next ? 'Log in to continue. We will take you straight back.' : 'Use your Student ID or university email.'}
          </p>

          {expired && !notice && (
            <Notice kind="warn" style={{ marginTop: 22 }}>
              Your session ended, so we signed you out. Please sign in again to carry on.
            </Notice>
          )}

          {notice && (
            <Notice kind={notice.kind} style={{ marginTop: 22 }}>{notice.text}</Notice>
          )}

          <form className="form" onSubmit={handleSubmit} noValidate>
            <Field id="username" label="Student ID or email" type="text"
                   autoComplete="username"
                   value={username} onChange={e => setUsername(e.target.value)} />

            <Field id="password" label="Password" type="password"
                   autoComplete="current-password"
                   value={password} onChange={e => setPassword(e.target.value)} />

            <div className="row-between">
              <label className="check">
                <input type="checkbox" checked={remember}
                       onChange={e => setRemember(e.target.checked)} />
                Remember me
              </label>
              <a className="text-link" href="#"
                 onClick={event => {
                   event.preventDefault()
                   setNotice({
                     kind: 'info',
                     text: 'Password reset is not available yet. Contact the help desk administrator to reset it for you.'
                   })
                 }}>
                Forgot password?
              </a>
            </div>

            <button type="submit" className="btn btn--primary btn--block" disabled={busy}>
              {busy ? 'Logging in…' : 'Log in'}
            </button>
          </form>

          <hr className="rule" />

          <p className="foot-note">New here? <Link to={next ? '/register?next=' + encodeURIComponent(next) : '/register'}>Create an account</Link></p>
        </div>
      </main>
    </div>
  )
}
