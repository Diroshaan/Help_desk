import { useState } from 'react'
import { Link } from 'react-router-dom'
import { API, errorMessage, request } from '../api.js'
import { Field, Notice } from '../components/Bits.jsx'
import { Sidebar } from '../components/Sidebar.jsx'
import { useSession } from '../hooks/useSession.jsx'
import { homeFor } from '../routes.jsx'

/**
 * Change password, for every role (officers and admins are given their first
 * password by an admin). The server ends the account's other sessions and the
 * PasswordChangedEvent (Observer) sends a "password changed" notification.
 * A wrong current password comes back as 400, so the user stays signed in.
 */
export default function ChangePassword() {
  const { role } = useSession()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  function set(name) {
    return event => setForm(current => ({ ...current, [name]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setErrors({}); setNotice(null)

    // the server only gets one copy, so the match is checked here
    if (form.newPassword !== form.confirm) {
      setErrors({ confirm: 'The two new passwords do not match.' })
      return
    }

    setBusy(true)
    try {
      const result = await request(API.password, {
        method: 'PUT',
        body: { currentPassword: form.currentPassword, newPassword: form.newPassword }
      })

      if (result.status === 204 || result.ok) {
        // don't leave passwords sitting in the form
        setForm({ currentPassword: '', newPassword: '', confirm: '' })
        setNotice({
          kind: 'info',
          text: 'Your password has been changed. Any other device signed in to this account has been signed out.'
        })
        return
      }

      setNotice({ kind: 'error', text: errorMessage(result, 'We could not change your password.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Your password was not changed.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <Link className="back-link" to={role === 'STUDENT' ? '/profile' : homeFor(role)}>&larr; Back</Link>
          <div className="page-head"><h1>Change password</h1></div>
          <p className="lede">
            Use at least 8 characters with upper and lower case letters and a number.
            You stay signed in here; every other session is ended.
          </p>

          {notice && <Notice kind={notice.kind} style={{ marginTop: 18 }}>{notice.text}</Notice>}

          <form className="form" onSubmit={handleSubmit} noValidate style={{ maxWidth: 440 }}>
            <Field id="currentPassword" label="Current password" type="password"
                   autoComplete="current-password"
                   value={form.currentPassword} onChange={set('currentPassword')}
                   error={errors.currentPassword} />

            <Field id="newPassword" label="New password" type="password" autoComplete="new-password"
                   value={form.newPassword} onChange={set('newPassword')} error={errors.newPassword} />

            <Field id="confirm" label="Confirm new password" type="password" autoComplete="new-password"
                   value={form.confirm} onChange={set('confirm')} error={errors.confirm} />

            <div className="btn-row">
              <button type="submit" className="btn btn--primary"
                      disabled={busy || !form.currentPassword || !form.newPassword}>
                {busy ? 'Changing…' : 'Change password'}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  )
}
