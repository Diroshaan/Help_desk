import { Fragment, useEffect, useState } from 'react'
import { API, errorMessage, fieldErrors, formatDateTime, request, withQuery } from '../../api.js'
import { Field, Notice, SelectField, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const ROLE_FILTERS = [
  { value: '', label: 'All roles' },
  { value: 'STUDENT', label: 'Students' },
  { value: 'OFFICER', label: 'Officers' },
  { value: 'ADMIN', label: 'Administrators' }
]

const EMPTY_OFFICER = { email: '', password: '', staffNumber: '', jobTitle: '', fullName: '', departmentCodes: [] }
const EMPTY_ADMIN = { email: '', password: '', displayName: '', staffNumber: '' }

/* F6-N3: one checkbox per open department. The backend refuses an officer with
   no department (an officer who serves nothing cannot see routed work), so the
   form offers the choice rather than letting the request fail for a reason the
   administrator was never shown. Codes are what the API takes; names are what a
   person reads. */
function DepartmentPicker({ id, departments, selected, onChange }) {
  function toggle(code) {
    onChange(selected.includes(code) ? selected.filter(c => c !== code) : [...selected, code])
  }

  return (
    <div className="field" role="group" aria-labelledby={id + '-label'}>
      <label id={id + '-label'}>Departments this officer serves</label>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px 22px', marginTop: 6 }}>
        {departments.map(department => (
          <label className="check" key={department.code}>
            <input type="checkbox" checked={selected.includes(department.code)}
                   onChange={() => toggle(department.code)} />
            {department.name}
          </label>
        ))}
      </div>
      {departments.length === 0 && <p className="hint">Loading departments…</p>}
    </div>
  )
}

/* The officer part of a row's subtitle. An EMPTY list is the broken state F6-N3
   exists to make visible, so it is said in words rather than shown as nothing. */
function departmentsText(user, departments) {
  if (!Array.isArray(user.departmentCodes)) return ''
  if (user.departmentCodes.length === 0) return ' · Serves no department'
  const names = user.departmentCodes.map(code => departments.find(d => d.code === code)?.name || code)
  return ' · ' + names.join(', ')
}

export default function Users() {
  const [roleFilter, setRoleFilter] = useState('')
  const [users, setUsers] = useState([])
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)

  const [officerForm, setOfficerForm] = useState(EMPTY_OFFICER)
  const [officerErrors, setOfficerErrors] = useState({})
  const [adminForm, setAdminForm] = useState(EMPTY_ADMIN)
  const [adminErrors, setAdminErrors] = useState({})

  // { id, codes } while an officer's departments are being edited in place.
  const [editing, setEditing] = useState(null)

  async function load() {
    setLoading(true); setError('')
    const result = await request(withQuery(API.adminUsers, { role: roleFilter || undefined }))
    if (result.ok) setUsers(result.data)
    else setError('We could not load the user list.')
    setLoading(false)
  }

  useEffect(() => { load() }, [roleFilter])

  useEffect(() => {
    request(API.departments).then(result => {
      if (result.ok) setDepartments(result.data)
    })
  }, [])

  function replaceUser(updated) {
    setUsers(current => current.map(u => u.id === updated.id ? updated : u))
  }

  async function toggleActive(user) {
    setBusy('user-' + user.id)
    const result = await request(API.adminUserStatus(user.id), { method: 'PATCH', body: { active: !user.active } })
    if (result.ok) {
      replaceUser(result.data)
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not update that account.') })
    }
    setBusy(null)
  }

  async function saveDepartments() {
    setBusy('departments-' + editing.id); setNotice(null)
    const result = await request(API.adminOfficerDepartments(editing.id),
      { method: 'PUT', body: { departmentCodes: editing.codes } })
    if (result.ok) {
      replaceUser(result.data)
      setEditing(null)
      setNotice({ kind: 'info', text: 'Departments updated.' })
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not update those departments.') })
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
      const fields = fieldErrors(result, ['email', 'password', 'staffNumber', 'jobTitle', 'fullName', 'departmentCodes'])
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
              <DepartmentPicker id="officerDepartments" departments={departments}
                                selected={officerForm.departmentCodes}
                                onChange={codes => setOfficerForm(f => ({ ...f, departmentCodes: codes }))} />
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
              <Fragment key={user.id}>
                <div className="pref">
                  <div className="pref__text">
                    <strong>{user.label}</strong>
                    <span>
                      {user.email} · {user.role}{user.reference ? ' · ' + user.reference : ''}
                      {departmentsText(user, departments)} · Since {formatDateTime(user.createdAt)}
                    </span>
                    {user.provisionedBy && <span>Provisioned by {user.provisionedBy}</span>}
                  </div>
                  <div className="row-side">
                    <StatusPill value={user.active ? 'ACTIVE' : 'INACTIVE'}
                                label={user.removed ? 'Removed' : user.active ? 'Active' : 'Suspended'} />
                    {/* No department editor for a removed officer: they have left,
                        and the backend refuses the change anyway (PR #56 review). */}
                    {!user.removed && user.role === 'OFFICER' && (
                      <button type="button" className="btn btn--ghost"
                              onClick={() => setEditing(editing?.id === user.id ? null : { id: user.id, codes: user.departmentCodes || [] })}>
                        {editing?.id === user.id ? 'Close' : 'Departments'}
                      </button>
                    )}
                    <button type="button" className="btn btn--ghost" disabled={busy === 'user-' + user.id}
                            onClick={() => toggleActive(user)}>
                      {user.active ? 'Suspend' : 'Reactivate'}
                    </button>
                  </div>
                </div>

                {editing?.id === user.id && (
                  <div style={{ padding: '0 0 16px' }}>
                    <DepartmentPicker id={'editDepartments' + user.id} departments={departments}
                                      selected={editing.codes}
                                      onChange={codes => setEditing(current => ({ ...current, codes }))} />
                    <div className="btn-row">
                      <button type="button" className="btn btn--primary"
                              disabled={busy === 'departments-' + user.id} onClick={saveDepartments}>
                        {busy === 'departments-' + user.id ? 'Saving…' : 'Save departments'}
                      </button>
                    </div>
                  </div>
                )}
              </Fragment>
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
