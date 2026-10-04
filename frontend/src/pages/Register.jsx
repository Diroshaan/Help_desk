import { useMemo, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { API, errorMessage, fieldErrors, request } from '../api.js'
import { BrandPanel, Field, Notice, PhoneList, SelectField } from '../components/Bits.jsx'
import { nextFrom } from '../routes.jsx'

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

/** Rough strength score for the meter (0-5). It never blocks submit; the server enforces the rule. */
function scorePassword(value) {
  let score = 0
  if (value.length >= 8) score++
  if (value.length >= 12) score++
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++
  if (/[0-9]/.test(value)) score++
  if (/[^A-Za-z0-9]/.test(value)) score++
  return score
}

export default function Register() {
  const navigate = useNavigate()
  const location = useLocation()
  const next = nextFrom(location.search)

  const [form, setForm] = useState({
    givenName: '', surname: '', studentId: '', email: '', department: '',
    password: '', confirm: ''
  })
  // up to three contact numbers
  const [phones, setPhones] = useState([''])
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

    // mark every empty field first, not just the policy message
    const missing = {}
    if (!form.givenName.trim()) missing.givenName = 'Enter your given name(s).'
    if (!form.surname.trim()) missing.surname = 'Enter your surname.'
    if (!form.studentId.trim()) missing.studentId = 'Enter your Student ID.'
    if (!form.email.trim()) missing.email = 'Enter your university email.'
    if (!form.department) missing.department = 'Choose your faculty or department.'
    if (!form.password) missing.password = 'Choose a password.'
    if (Object.keys(missing).length) {
      setErrors(missing)
      return
    }

    if (form.password !== form.confirm) {
      setErrors({ confirm: 'The two passwords do not match.' })
      return
    }

    if (!terms) {
      setNotice({ kind: 'error', text: 'Please accept the acceptable use policy to continue.' })
      return
    }

    /* Separate given name and surname so the server doesn't have to guess where
   to split a full name. Blank phone boxes are sent too; the server drops them. */
    const payload = {
      givenName: form.givenName.trim(),
      surname: form.surname.trim(),
      studentId: form.studentId.trim(),
      email: form.email.trim(),
      phones: phones.map(p => p.trim()).filter(Boolean),
      department: form.department,
      password: form.password
    }

    setBusy(true)

    try {
      const result = await request(API.register, { method: 'POST', body: payload })

      if (result.status === 201 || result.ok) {
        /* Registering doesn't log you in. We pass the email to the login page in
   sessionStorage (never the password) so it can prefill it. */
        const saved = result.data || {}

        try {
          sessionStorage.setItem('unihelp.justRegistered', saved.email || payload.email || '')
        } catch {
          /* storage can be blocked; the account is still created */
        }

        // replace so Back doesn't return to the filled-in form
        navigate(next ? '/login?next=' + encodeURIComponent(next) : '/login', { replace: true })
        return
      }

      // 409 = Student ID or email already registered
      const fields = fieldErrors(result,
        ['givenName', 'surname', 'studentId', 'email', 'phones', 'department', 'password'])
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
            <div className="field-row">
              <Field id="givenName" label="Given name(s)" type="text" autoComplete="given-name"
                     value={form.givenName} onChange={set('givenName')} error={errors.givenName} />

              <Field id="surname" label="Surname" type="text" autoComplete="family-name"
                     value={form.surname} onChange={set('surname')} error={errors.surname} />
            </div>

            <Field id="studentId" label="Student ID" type="text" className="mono"
                   placeholder="ITxxxxxxxx"
                   value={form.studentId} onChange={set('studentId')} error={errors.studentId} />

            <PhoneList values={phones} onChange={setPhones} error={errors.phones} />

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

          <p className="foot-note">Already registered? <Link to={next ? '/login?next=' + encodeURIComponent(next) : '/login'}>Log in</Link></p>
        </div>
      </main>
    </div>
  )
}
