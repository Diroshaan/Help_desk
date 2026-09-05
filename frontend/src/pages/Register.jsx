import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { API, errorMessage, fieldErrors, request } from '../api.js'
import { BrandPanel, Field, Notice, SelectField } from '../components/Bits.jsx'

const POINTS = [
  { lead: 'One account, every desk.', body: ' IT, Finance, the Registrar and Hostel services — no separate logins to remember.' },
  { lead: 'Nothing happens in the dark.', body: ' See who picked your request up, what they changed, and when.' },
  { lead: 'You decide how we reach you.', body: ' Turn each kind of notification on or off from your profile.' }
]

const DEPARTMENTS = [
  { value: '', label: 'Choose one' },
  { value: 'Faculty of Computing', label: 'Faculty of Computing' },
  { value: 'Faculty of Engineering', label: 'Faculty of Engineering' },
  { value: 'Faculty of Business', label: 'Faculty of Business' },
  { value: 'Faculty of Humanities & Sciences', label: 'Faculty of Humanities & Sciences' },
  { value: 'Other', label: 'Other' }
]

/**
 * Four things make a password harder to guess: length, and the presence of
 * lower case, upper case, digits and symbols. Each scores a point. This only
 * informs the student — it never blocks the submit, because the rule that
 * actually protects the account is the one on the server.
 */
function scorePassword(value) {
  let score = 0
  if (value.length >= 8) score++
  if (value.length >= 12) score++
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++
  if (/[0-9]/.test(value)) score++
  if (/[^A-Za-z0-9]/.test(value)) score++
  return score            // 0 to 5
}

export default function Register() {
  const navigate = useNavigate()

  const [form, setForm] = useState({
    fullName: '', studentId: '', phone: '', email: '', department: '',
    password: '', confirm: ''
  })
  const [terms, setTerms] = useState(false)
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  function set(name) {
    return event => setForm(current => ({ ...current, [name]: event.target.value }))
  }

  const strength = useMemo(() => {
    if (!form.password) return { width: 0, colour: 'var(--danger)', label: 'At least 8 characters' }
    const score = scorePassword(form.password)
    if (score <= 2) return { width: score / 5 * 100, colour: 'var(--danger)', label: 'Weak' }
    if (score <= 3) return { width: score / 5 * 100, colour: 'var(--warn)', label: 'Fair' }
    return { width: score / 5 * 100, colour: 'var(--teal)', label: 'Strong' }
  }, [form.password])

  async function handleSubmit(event) {
    event.preventDefault()
    setErrors({})
    setNotice(null)

    // The two-password check has no server equivalent — the server only ever
    // receives one — so it has to happen here.
    if (form.password !== form.confirm) {
      setErrors({ confirm: 'The two passwords do not match.' })
      return
    }

    if (!terms) {
      setNotice({ kind: 'error', text: 'Please accept the acceptable use policy to continue.' })
      return
    }

    const payload = {
      fullName: form.fullName.trim(),
      studentId: form.studentId.trim(),
      email: form.email.trim(),
      phone: form.phone.trim(),
      department: form.department,
      password: form.password
    }

    setBusy(true)

    try {
      const result = await request(API.register, { method: 'POST', body: payload })

      if (result.status === 201 || result.ok) {
        /* Registration creates the account. It does not create a session:
           POST /api/students writes the row, and only POST /api/auth/login
           puts an Authentication in it. So the next stop is the login page,
           and the confirmation belongs there — shown to someone who has just
           registered, and to nobody else.

           What travels with them is the email, because that is what the login
           matches on. sessionStorage belongs to this browser tab alone and is
           discarded when the tab closes; the password is never stored. */
        const saved = result.data || {}

        try {
          sessionStorage.setItem('unihelp.justRegistered', saved.email || payload.email || '')
        } catch {
          /* Private browsing can refuse storage. The greeting and the prefill
             are courtesies; losing them must not block a registration that
             already succeeded. */
        }

        // replace, not push: the back button should not return to a filled-in
        // form for an account that now exists.
        navigate('/login', { replace: true })
        return
      }

      // 409 is the duplicate Student ID / email case.
      /* Only these inputs exist on this form. Anything else in the body is
         Spring's envelope, and passing the list keeps it out. */
      const fields = fieldErrors(result,
        ['fullName', 'studentId', 'email', 'phone', 'department', 'password'])
      if (Object.keys(fields).length) {
        setErrors(fields)
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not create the account.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. Check that the application is running.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="split">
      <BrandPanel heading="Ask once. We take it from there." points={POINTS} />

      <main className="form-side">
        <div className="form-col form-col--wide rise rise-2">
          <Link className="back-link" to="/">&larr; Back to help desk</Link>

          <h1>Create an account</h1>
          <p className="lede">Registration is open to enrolled students only.</p>

          {notice && <Notice kind={notice.kind} style={{ marginTop: 22 }}>{notice.text}</Notice>}

          <form className="form" onSubmit={handleSubmit} noValidate>
            <Field id="fullName" label="Full name" type="text" autoComplete="name"
                   value={form.fullName} onChange={set('fullName')} error={errors.fullName} />

            <div className="field-row">
              <Field id="studentId" label="Student ID" type="text" className="mono"
                     placeholder="ITxxxxxxxx"
                     value={form.studentId} onChange={set('studentId')} error={errors.studentId} />

              <Field id="phone" label="Phone" type="tel" autoComplete="tel"
                     value={form.phone} onChange={set('phone')} error={errors.phone} />
            </div>

            <Field id="email" label="University email" type="email" autoComplete="email"
                   value={form.email} onChange={set('email')} error={errors.email} />

            <SelectField id="department" label="Faculty or department" options={DEPARTMENTS}
                         value={form.department} onChange={set('department')} error={errors.department} />

            <Field id="password" label="Password" type="password" autoComplete="new-password"
                   value={form.password} onChange={set('password')} error={errors.password}>
              <div className="row-between" style={{ gap: 10 }}>
                <div style={{ flex: 1, height: 3, background: 'var(--line)', borderRadius: 2, overflow: 'hidden' }}>
                  <div style={{
                    height: '100%',
                    width: strength.width + '%',
                    background: strength.colour,
                    transition: 'width .22s cubic-bezier(.22,.61,.36,1), background .22s'
                  }} />
                </div>
                <span className="hint">{strength.label}</span>
              </div>
            </Field>

            <Field id="confirm" label="Confirm password" type="password" autoComplete="new-password"
                   value={form.confirm} onChange={set('confirm')} error={errors.confirm} />

            <label className="check">
              <input type="checkbox" checked={terms} onChange={e => setTerms(e.target.checked)} />
              I agree to the acceptable use policy for university IT services.
            </label>

            <button type="submit" className="btn btn--primary btn--block" disabled={busy}>
              {busy ? 'Creating account…' : 'Create account'}
            </button>
          </form>

          <hr className="rule" />

          <p className="foot-note">Already registered? <Link to="/login">Log in</Link></p>
        </div>
      </main>
    </div>
  )
}
