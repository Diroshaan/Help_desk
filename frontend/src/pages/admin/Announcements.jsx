import { useEffect, useState } from 'react'
import { API, errorMessage, fieldErrors, formatDateTime, request } from '../../api.js'
import { Field, Notice, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const ROLES = ['STUDENT', 'OFFICER', 'ADMIN']
const EMPTY_FORM = { title: '', body: '', expiresAt: '', visibleToRoles: [] }

export default function Announcements() {
  const [announcements, setAnnouncements] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)

  const [editingId, setEditingId] = useState(null)   // null while creating
  const [form, setForm] = useState(EMPTY_FORM)
  const [errors, setErrors] = useState({})

  async function load() {
    setLoading(true)
    const result = await request(API.adminAnnouncements)
    if (result.ok) setAnnouncements(result.data)
    else setError('We could not load announcements.')
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  function startEdit(announcement) {
    setEditingId(announcement.id)
    setErrors({}); setNotice(null)
    setForm({
      title: announcement.title,
      body: announcement.body,
      expiresAt: announcement.expiresAt ? announcement.expiresAt.slice(0, 16) : '',
      visibleToRoles: announcement.visibleToRoles || []
    })
  }

  function resetForm() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setErrors({})
  }

  function toggleRole(role) {
    setForm(current => ({
      ...current,
      visibleToRoles: current.visibleToRoles.includes(role)
        ? current.visibleToRoles.filter(r => r !== role)
        : [...current.visibleToRoles, role]
    }))
  }

  async function save(event) {
    event.preventDefault()
    setErrors({}); setNotice(null); setBusy('save')

    const payload = {
      title: form.title.trim(),
      body: form.body.trim(),
      expiresAt: form.expiresAt ? form.expiresAt + ':00' : null,
      visibleToRoles: form.visibleToRoles
    }

    const result = editingId
      ? await request(API.adminAnnouncement(editingId), { method: 'PUT', body: payload })
      : await request(API.adminAnnouncements, { method: 'POST', body: payload })

    if (result.ok) {
      if (editingId) setAnnouncements(current => current.map(a => a.id === editingId ? result.data : a))
      else setAnnouncements(current => [result.data, ...current])
      resetForm()
      setNotice({ kind: 'info', text: 'Saved.' })
    } else {
      const fields = fieldErrors(result, ['title', 'body', 'expiresAt'])
      if (Object.keys(fields).length) setErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save that announcement.') })
    }
    setBusy(null)
  }

  async function remove(id) {
    setBusy('delete-' + id)
    const result = await request(API.adminAnnouncement(id), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setAnnouncements(current => current.filter(a => a.id !== id))
      if (editingId === id) resetForm()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not delete that announcement.') })
    }
    setBusy(null)
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Announcements</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <h2>{editingId ? 'Edit announcement' : 'New announcement'}</h2>
            <form className="form" style={{ marginTop: 0 }} onSubmit={save} noValidate>
              <Field id="title" label="Title" type="text"
                     value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                     error={errors.title} />

              <div className="field">
                <label htmlFor="body">Body</label>
                <textarea id="body" rows={5} value={form.body}
                          onChange={e => setForm(f => ({ ...f, body: e.target.value }))} />
                <p className="field-error">{errors.body || ''}</p>
              </div>

              <Field id="expiresAt" label="Expires (optional)" type="datetime-local"
                     value={form.expiresAt} onChange={e => setForm(f => ({ ...f, expiresAt: e.target.value }))}
                     error={errors.expiresAt} hint="Leave blank for a standing notice with no expiry." />

              <div className="field">
                <label>Visible to</label>
                <div className="chips">
                  {ROLES.map(role => (
                    <button type="button" key={role} className="chip"
                            aria-current={form.visibleToRoles.includes(role)}
                            onClick={() => toggleRole(role)}>
                      {role}
                    </button>
                  ))}
                </div>
                <p className="hint">Leave every role unselected to show it to everybody.</p>
              </div>

              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'save'}>
                  {busy === 'save' ? 'Saving…' : editingId ? 'Save changes' : 'Publish announcement'}
                </button>
                {editingId && (
                  <button type="button" className="btn btn--ghost" onClick={resetForm}>Cancel</button>
                )}
              </div>
            </form>
          </section>

          <section className="section">
            <h2>All announcements</h2>

            {error && <Notice kind="error">{error}</Notice>}
            {!error && loading && <p className="empty">Loading…</p>}
            {!error && !loading && announcements.length === 0 && <p className="empty">No announcements yet.</p>}

            {!error && !loading && announcements.map(a => (
              <div className="pref" key={a.id}>
                <div className="pref__text">
                  <strong>{a.title}</strong>
                  <span>
                    By {a.publishedByName} · Published {formatDateTime(a.publishedAt)}
                    {a.expiresAt ? ' · Expires ' + formatDateTime(a.expiresAt) : ' · No expiry'}
                    {a.visibleToRoles?.length ? ' · ' + a.visibleToRoles.join(', ') : ' · Everyone'}
                  </span>
                </div>
                <div className="row-side">
                  {a.expired && <StatusPill value="EXPIRED" label="Expired" />}
                  <button type="button" className="btn btn--ghost" onClick={() => startEdit(a)}>Edit</button>
                  <button type="button" className="btn btn--ghost" disabled={busy === 'delete-' + a.id}
                          onClick={() => remove(a.id)}>Delete</button>
                </div>
              </div>
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
