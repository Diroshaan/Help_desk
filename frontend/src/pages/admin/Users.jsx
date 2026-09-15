import { useEffect, useState } from 'react'
import { API, errorMessage, fieldErrors, formatDateTime, request, withQuery } from '../../api.js'
import { Field, Notice, SelectField, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const ROLE_FILTERS = [
  { value: '', label: 'All roles' },
  { value: 'STUDENT', label: 'Students' },
  { value: 'OFFICER', label: 'Officers' },
  { value: 'ADMIN', label: 'Administrators' }
]

const EMPTY_OFFICER = { email: '', password: '', staffNumber: '', jobTitle: '', fullName: '' }
const EMPTY_ADMIN = { email: '', password: '', displayName: '', staffNumber: '' }

export default function Users() {
  const [roleFilter, setRoleFilter] = useState('')
  const [users, setUsers] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)

  const [officerForm, setOfficerForm] = useState(EMPTY_OFFICER)
  const [officerErrors, setOfficerErrors] = useState({})
  const [adminForm, setAdminForm] = useState(EMPTY_ADMIN)
  const [adminErrors, setAdminErrors] = useState({})

  async function load() {
    setLoading(true); setError('')
    const result = await request(withQuery(API.adminUsers, { role: roleFilter || undefined }))
    if (result.ok) setUsers(result.data)
    else setError('We could not load the user list.')
    setLoading(false)
  }

  useEffect(() => { load() }, [roleFilter])

  async function toggleActive(user) {
    setBusy('user-' + user.id)
    const result = await request(API.adminUserStatus(user.id), { method: 'PATCH', body: { active: !user.active } })
    if (result.ok) {
      setUsers(current => current.map(u => u.id === user.id ? result.data : u))
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not update that account.') })
    }
    setBusy(null)
  }

  async function provisionOfficer(event) {
    event.preventDefault()
    setOfficerErrors({}); setNotice(null); setBusy('officer')

    const result = await request(API.adminOfficers, { method: 'POST', body: officerForm })
    if (result.ok) {
      setUsers(current => [result.data, ...current])
      setOfficerForm(EMPTY_OFFICER)
      setNotice({ kind: 'info', text: 'Officer account created.' })
    } else {
      const fields = fieldErrors(result, ['email', 'password', 'staffNumber', 'jobTitle', 'fullName'])
      if (Object.keys(fields).length) setOfficerErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not create that officer account.') })
    }
    setBusy(null)
  }

  async function provisionAdmin(event) {
    event.preventDefault()
    setAdminErrors({}); setNotice(null); setBusy('admin')

    const result = await request(API.adminAdministrators, { method: 'POST', body: adminForm })
    if (result.ok) {
      setUsers(current => [result.data, ...current])
      setAdminForm(EMPTY_ADMIN)
      setNotice({ kind: 'info', text: 'Administrator account created.' })
    } else {
      const fields = fieldErrors(result, ['email', 'password', 'displayName', 'staffNumber'])
      if (Object.keys(fields).length) setAdminErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not create that administrator account.') })
    }
    setBusy(null)
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Users</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <h2>Provision an officer</h2>
            <form className="form" style={{ marginTop: 0 }} onSubmit={provisionOfficer} noValidate>
              <div className="field-row">
                <Field id="officerFullName" label="Full name" type="text"
                       value={officerForm.fullName} onChange={e => setOfficerForm(f => ({ ...f, fullName: e.target.value }))}
                       error={officerErrors.fullName} />
                <Field id="officerEmail" label="Email" type="email"
                       value={officerForm.email} onChange={e => setOfficerForm(f => ({ ...f, email: e.target.value }))}
                       error={officerErrors.email} />
              </div>
              <div className="field-row">
                <Field id="officerStaffNumber" label="Staff number" type="text"
                       value={officerForm.staffNumber} onChange={e => setOfficerForm(f => ({ ...f, staffNumber: e.target.value }))}
                       error={officerErrors.staffNumber} />
                <Field id="officerJobTitle" label="Job title" type="text"
                       value={officerForm.jobTitle} onChange={e => setOfficerForm(f => ({ ...f, jobTitle: e.target.value }))}
                       error={officerErrors.jobTitle} />
              </div>
              <Field id="officerPassword" label="Temporary password" type="password"
                     value={officerForm.password} onChange={e => setOfficerForm(f => ({ ...f, password: e.target.value }))}
                     error={officerErrors.password}
                     hint="At least 8 characters, with an upper case letter, a lower case letter and a digit." />
              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'officer'}>
                  {busy === 'officer' ? 'Creating…' : 'Create officer account'}
                </button>
              </div>
            </form>
          </section>

          <section className="section">
            <h2>Provision an administrator</h2>
            <form className="form" style={{ marginTop: 0 }} onSubmit={provisionAdmin} noValidate>
              <div className="field-row">
                <Field id="adminDisplayName" label="Display name" type="text"
                       value={adminForm.displayName} onChange={e => setAdminForm(f => ({ ...f, displayName: e.target.value }))}
                       error={adminErrors.displayName} />
                <Field id="adminEmail" label="Email" type="email"
                       value={adminForm.email} onChange={e => setAdminForm(f => ({ ...f, email: e.target.value }))}
                       error={adminErrors.email} />
              </div>
              <Field id="adminStaffNumber" label="Staff number (optional)" type="text"
                     value={adminForm.staffNumber} onChange={e => setAdminForm(f => ({ ...f, staffNumber: e.target.value }))}
                     error={adminErrors.staffNumber} />
              <Field id="adminPassword" label="Temporary password" type="password"
                     value={adminForm.password} onChange={e => setAdminForm(f => ({ ...f, password: e.target.value }))}
                     error={adminErrors.password}
                     hint="At least 8 characters, with an upper case letter, a lower case letter and a digit." />
              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'admin'}>
                  {busy === 'admin' ? 'Creating…' : 'Create administrator account'}
                </button>
              </div>
            </form>
          </section>

          <section className="section">
            <h2>All accounts</h2>
            <SelectField id="roleFilter" label="Filter by role" options={ROLE_FILTERS}
                         value={roleFilter} onChange={e => setRoleFilter(e.target.value)} />

            {error && <Notice kind="error" style={{ marginTop: 16 }}>{error}</Notice>}
            {!error && loading && <p className="empty">Loading…</p>}
            {!error && !loading && users.length === 0 && <p className="empty">No accounts found.</p>}

            {!error && !loading && users.map(user => (
              <div className="pref" key={user.id}>
                <div className="pref__text">
                  <strong>{user.label}</strong>
                  <span>{user.email} · {user.role}{user.reference ? ' · ' + user.reference : ''} · Since {formatDateTime(user.createdAt)}</span>
                </div>
                <div className="row-side">
                  <StatusPill value={user.active ? 'ACTIVE' : 'INACTIVE'} label={user.active ? 'Active' : 'Suspended'} />
                  <button type="button" className="btn btn--ghost" disabled={busy === 'user-' + user.id}
                          onClick={() => toggleActive(user)}>
                    {user.active ? 'Suspend' : 'Reactivate'}
                  </button>
                </div>
              </div>
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
