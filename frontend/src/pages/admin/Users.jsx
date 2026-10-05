import { Fragment, useEffect, useState } from 'react'
import { API, errorMessage, fieldErrors, formatDateTime, request, withQuery } from '../../api.js'
import { ConfirmButton, Field, Notice, SelectField, StatusPill } from '../../components/Bits.jsx'
import { useSession } from '../../hooks/useSession.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const ROLE_FILTERS = [
  { value: '', label: 'All roles' },
  { value: 'STUDENT', label: 'Students' },
  { value: 'OFFICER', label: 'Officers' },
  { value: 'ADMIN', label: 'Administrators' }
]

const EMPTY_OFFICER = { email: '', password: '', staffNumber: '', jobTitle: '', fullName: '', departmentCodes: [] }
const EMPTY_ADMIN = { email: '', password: '', displayName: '', staffNumber: '' }

/* One checkbox per department. The backend refuses an officer with no
   department, so the form asks for at least one up front. */
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

/* Officer departments for the row subtitle; an empty list is spelled out. */
function departmentsText(user, departments) {
  if (!Array.isArray(user.departmentCodes)) return ''
  if (user.departmentCodes.length === 0) return ' · Serves no department'
  const names = user.departmentCodes.map(code => departments.find(d => d.code === code)?.name || code)
  return ' · ' + names.join(', ')
}

/** Officer's supervisor, loaded when the row is opened. Choices are the other active officers. */
function SupervisorPicker({ officer, officers, onSaved, onError }) {
  const [current, setCurrent] = useState(undefined)   // undefined = loading, null = none / unavailable
  const [choice, setChoice] = useState('')
  const [unavailable, setUnavailable] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    request(API.adminOfficerSupervisor(officer.id)).then(result => {
      if (result.ok && result.data) {
        setCurrent(result.data)
        setChoice(result.data.supervisorId ? String(result.data.supervisorId) : '')
      } else {
        setCurrent(null)
        setUnavailable(!result.ok)
      }
    }).catch(() => { setCurrent(null); setUnavailable(true) })
  }, [officer.id])

  async function save() {
    setBusy(true)
    const result = await request(API.adminOfficerSupervisor(officer.id), {
      method: 'PUT', body: { supervisorId: choice ? Number(choice) : null }
    })
    if (result.ok) { setCurrent(result.data); onSaved('Supervisor updated.') }
    else onError(errorMessage(result, 'We could not set that supervisor.'))
    setBusy(false)
  }

  if (unavailable) return <p className="hint">Supervisors can be set once the queue update is live.</p>

  const candidates = officers.filter(o => o.id !== officer.id && o.active && !o.removed)
  return (
    <div className="field-row" style={{ alignItems: 'end' }}>
      <SelectField id={'supervisor' + officer.id} label="Supervisor"
                   value={choice} onChange={e => setChoice(e.target.value)}
                   options={[{ value: '', label: current === undefined ? 'Loading…' : 'No supervisor' },
                             ...candidates.map(o => ({ value: String(o.id), label: o.label }))]} />
      <div className="btn-row" style={{ paddingBottom: 6 }}>
        <button type="button" className="btn btn--ghost" disabled={busy || current === undefined} onClick={save}>
          {busy ? 'Saving…' : 'Save supervisor'}
        </button>
      </div>
    </div>
  )
}

/** Same temporary-password rule as the server. */
function passwordProblem(password) {
  if (!password) return 'Set a temporary password.'
  if (password.length < 8 || !/[A-Z]/.test(password) || !/[a-z]/.test(password) || !/[0-9]/.test(password)) {
    return 'At least 8 characters, with an upper case letter, a lower case letter and a digit.'
  }
  return null
}

export default function Users() {
  const { user: me } = useSession()
  const [roleFilter, setRoleFilter] = useState('')
  // removed accounts are hidden unless asked for
  const [showRemoved, setShowRemoved] = useState(false)
  const [officers, setOfficers] = useState([])
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

  // { id, codes } while editing an officer's departments
  const [editing, setEditing] = useState(null)

  async function load() {
    setLoading(true); setError('')
    const result = await request(withQuery(API.adminUsers, {
      role: roleFilter || undefined,
      includeRemoved: showRemoved ? 'true' : undefined
    }))
    if (result.ok) {
      // also filter here in case the server ignores includeRemoved
      setUsers(showRemoved ? result.data : result.data.filter(u => !u.removed))
    } else {
      setError('We could not load the user list.')
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [roleFilter, showRemoved])

  // supervisor picker needs all officers, whatever the filter
  useEffect(() => {
    request(withQuery(API.adminUsers, { role: 'OFFICER' })).then(result => {
      if (result.ok) setOfficers(result.data)
    })
  }, [])

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

  async function removeAccount(user) {
    setBusy('remove-' + user.id); setNotice(null)
    const result = await request(API.adminUser(user.id), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      if (showRemoved) await load()
      else setUsers(current => current.filter(u => u.id !== user.id))
      setNotice({ kind: 'info', text: user.label + "'s account has been removed. They can no longer sign in." })
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not remove that account.') })
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
    setOfficerErrors({}); setNotice(null)
    // check here so each empty field gets its own message
    const missing = {}
    if (!officerForm.fullName.trim()) missing.fullName = 'Enter the officer\'s full name.'
    if (!officerForm.email.trim()) missing.email = 'Enter their email.'
    if (!officerForm.staffNumber.trim()) missing.staffNumber = 'Enter their staff number.'
    if (!officerForm.jobTitle.trim()) missing.jobTitle = 'Enter their job title.'
    const officerPassword = passwordProblem(officerForm.password)
    if (officerPassword) missing.password = officerPassword
    if (Object.keys(missing).length) { setOfficerErrors(missing); return }
    setBusy('officer')

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
    setAdminErrors({}); setNotice(null)
    const missing = {}
    if (!adminForm.displayName.trim()) missing.displayName = 'Enter a display name.'
    if (!adminForm.email.trim()) missing.email = 'Enter their email.'
    const adminPassword = passwordProblem(adminForm.password)
    if (adminPassword) missing.password = adminPassword
    if (Object.keys(missing).length) { setAdminErrors(missing); return }
    setBusy('admin')

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
            <div className="row-between" style={{ alignItems: 'end' }}>
              <div style={{ flex: '1 1 240px' }}>
                <SelectField id="roleFilter" label="Filter by role" options={ROLE_FILTERS}
                             value={roleFilter} onChange={e => setRoleFilter(e.target.value)} />
              </div>
              <label className="check" style={{ paddingBottom: 18 }}>
                <input type="checkbox" checked={showRemoved} onChange={e => setShowRemoved(e.target.checked)} />
                Show removed accounts
              </label>
            </div>

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
                    <StatusPill value={user.active && !user.removed ? 'ACTIVE' : 'INACTIVE'}
                                label={user.removed ? 'Removed' : user.active ? 'Active' : 'Suspended'} />
                    {/* removed officers can't have departments changed */}
                    {!user.removed && user.role === 'OFFICER' && (
                      <button type="button" className="btn btn--ghost"
                              onClick={() => setEditing(editing?.id === user.id ? null : { id: user.id, codes: user.departmentCodes || [] })}>
                        {editing?.id === user.id ? 'Close' : 'Departments'}
                      </button>
                    )}
                    {/* removed is final; admins can't suspend or remove themselves */}
                    {!user.removed && me?.id !== user.id && (
                      <button type="button" className="btn btn--ghost" disabled={busy === 'user-' + user.id}
                              onClick={() => toggleActive(user)}>
                        {user.active ? 'Suspend' : 'Reactivate'}
                      </button>
                    )}
                    {!user.removed && me?.id !== user.id && (
                      <ConfirmButton label="Remove" confirmLabel="Yes, remove"
                                     question={'Remove ' + user.label + '? This is final.'}
                                     busy={busy === 'remove-' + user.id}
                                     onConfirm={() => removeAccount(user)} />
                    )}
                    {me?.id === user.id && <span className="tag">You</span>}
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
                    <div style={{ marginTop: 18 }}>
                      <SupervisorPicker officer={user} officers={officers}
                                        onSaved={text => setNotice({ kind: 'info', text })}
                                        onError={text => setNotice({ kind: 'error', text })} />
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
