import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API, errorMessage, request } from '../api.js'
import { BrandPanel, Field, Notice } from '../components/Bits.jsx'
import { useSession } from '../hooks/useSession.jsx'

const POINTS = [
  'Tickets routed automatically to the department that owns them.',
  'Every response, status change and resolution in one thread.',
  'Save articles and tickets into your own folders.'
]

export default function Login() {
  const navigate = useNavigate()
  const { refresh } = useSession()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)      // { kind, text }

  /* --------------------------------------------------------------------
     Arriving straight from registration

     Register leaves the new account's email in sessionStorage on its way
     here. If it is there, fill it in and say why — and only then, so a
     normal visit to this page shows nothing.

     The value is removed as soon as it is read, so a refresh does not
     repeat the message.
     -------------------------------------------------------------------- */
  useEffect(() => {
    let justRegistered = null

    try {
      justRegistered = sessionStorage.getItem('unihelp.justRegistered')
      sessionStorage.removeItem('unihelp.justRegistered')
    } catch {
      return      // storage unavailable; the form simply starts empty
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

    // Check before sending, so an empty form never costs a round trip.
    if (!username.trim() || !password) {
      setNotice({ kind: 'error', text: 'Enter both your Student ID (or email) and your password.' })
      return
    }

    setBusy(true)

    try {
      /* The one value typed is sent under both names the controller might
         expect. AuthController's LoginRequest calls this field either
         `username` or `email`, and Spring Boot ignores JSON properties a DTO
         does not declare, so whichever it uses binds and the other is dropped.
         Trim this to the real name once the DTO is confirmed. */
      const result = await request(API.login, {
        method: 'POST',
        body: { username: username.trim(), email: username.trim(), password }
      })

      if (result.ok) {
        /* Re-ask who is logged in before navigating, so the welcome page's top
           bar already knows the answer when it renders. Without this it would
           paint Login and Register for a moment and then swap. */
        await refresh()
        navigate('/')
        return
      }

      /* A suspended account and a wrong password are different failures and
         must read differently. DisabledException is a sibling of
         BadCredentialsException, not a subclass, so the controller has to
         catch it separately — the 403 below is the suspended case. */
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
          <p className="lede">Use your Student ID or university email.</p>

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

          <p className="foot-note">New here? <Link to="/register">Create an account</Link></p>
        </div>
      </main>
    </div>
  )
}
