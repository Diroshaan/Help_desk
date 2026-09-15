import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API, errorMessage, fieldErrors, formatDateTime, request, requestForm } from '../../api.js'
import { Field, Notice, SelectField, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const PRIORITIES = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'URGENT', label: 'Urgent' }
]

export default function TicketDetail() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [ticket, setTicket] = useState(null)
  const [categories, setCategories] = useState([])
  const [form, setForm] = useState(null)
  const [attachments, setAttachments] = useState([])
  const [bookmark, setBookmark] = useState(null)   // BookmarkResponse or null
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  async function load() {
    setLoading(true)
    const [ticketResult, attachmentResult, bookmarkResult, categoryResult] = await Promise.all([
      request(API.ticket(id)),
      request(API.ticketAttachments(id)),
      request(API.bookmarks),
      request(API.ticketCategories)
    ])

    if (!ticketResult.ok) {
      setNotFound(true)
      setLoading(false)
      return
    }

    setTicket(ticketResult.data)
    setForm({
      subject: ticketResult.data.subject,
      description: ticketResult.data.description,
      category: ticketResult.data.category,
      priority: ticketResult.data.priority
    })
    setAttachments(attachmentResult.ok ? attachmentResult.data : [])
    setCategories(categoryResult.ok ? categoryResult.data : [])

    if (bookmarkResult.ok) {
      const existing = (bookmarkResult.data || []).find(b => b.ticketId === Number(id))
      setBookmark(existing || null)
    }

    setLoading(false)
  }

  useEffect(() => { load() }, [id])

  function set(name) {
    return event => setForm(current => ({ ...current, [name]: event.target.value }))
  }

  async function saveChanges(event) {
    event.preventDefault()
    setErrors({}); setNotice(null); setBusy('save')

    try {
      const result = await request(API.ticket(id), { method: 'PUT', body: form })
      if (result.ok) {
        setTicket(result.data)
        setNotice({ kind: 'info', text: 'Your ticket has been updated.' })
      } else {
        const fields = fieldErrors(result, ['subject', 'description', 'category', 'priority'])
        if (Object.keys(fields).length) setErrors(fields)
        else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save those changes.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  async function withdraw() {
    setNotice(null); setBusy('withdraw')
    try {
      const result = await request(API.ticketWithdraw(id), { method: 'POST' })
      if (result.ok) {
        setTicket(result.data)
        setNotice({ kind: 'info', text: 'This ticket has been withdrawn.' })
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not withdraw this ticket.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  async function uploadFile(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setNotice(null); setBusy('upload')
    try {
      const form = new FormData()
      form.append('file', file)
      const result = await requestForm(API.ticketAttachments(id), { method: 'POST', form })
      if (result.ok) {
        setAttachments(current => [...current, result.data])
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not attach that file.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  async function deleteAttachment(attachmentId) {
    setBusy('attachment-' + attachmentId)
    try {
      const result = await request(API.ticketAttachment(id, attachmentId), { method: 'DELETE' })
      if (result.ok || result.status === 204) {
        setAttachments(current => current.filter(a => a.id !== attachmentId))
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not remove that attachment.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  async function toggleBookmark() {
    setBusy('bookmark')
    try {
      if (bookmark) {
        const result = await request(API.bookmark(bookmark.id), { method: 'DELETE' })
        if (result.ok || result.status === 204) setBookmark(null)
      } else {
        const result = await request(API.bookmarks, { method: 'POST', body: { ticketId: Number(id) } })
        if (result.ok) setBookmark(result.data)
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  if (loading) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col"><p className="empty">Loading ticket…</p></div>
    </main></div>
  }

  if (notFound) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col">
        <div className="page-head"><h1>Ticket not found</h1></div>
        <Notice kind="error" style={{ marginTop: 18 }}>
          We could not find that ticket, or it does not belong to you.
        </Notice>
        <div className="btn-row" style={{ marginTop: 20 }}>
          <Link className="btn btn--ghost" to="/tickets">Back to my tickets</Link>
        </div>
      </div>
    </main></div>
  }

  const editable = ticket.status === 'OPEN'
  const withdrawable = ticket.status === 'OPEN' || ticket.status === 'IN_PROGRESS'

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <Link className="back-link" to="/tickets">&larr; Back to my tickets</Link>

          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>{ticket.subject}</h1>
            <StatusPill value={ticket.status} />
          </div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <div className="detail-list">
              <div><dt>Category</dt><dd>{ticket.category}</dd></div>
              <div><dt>Priority</dt><dd>{ticket.priority}</dd></div>
              <div><dt>Submitted</dt><dd>{formatDateTime(ticket.createdAt)}</dd></div>
              <div><dt>Last updated</dt><dd>{formatDateTime(ticket.updatedAt)}</dd></div>
            </div>

            <div className="btn-row" style={{ marginTop: 20 }}>
              <button type="button" className="btn btn--ghost" onClick={toggleBookmark} disabled={busy === 'bookmark'}>
                {bookmark ? 'Remove bookmark' : 'Bookmark this ticket'}
              </button>
              {withdrawable && (
                <button type="button" className="btn btn--danger" onClick={withdraw} disabled={busy === 'withdraw'}>
                  {busy === 'withdraw' ? 'Withdrawing…' : 'Withdraw ticket'}
                </button>
              )}
            </div>
          </section>

          <section className="section">
            <h2>{editable ? 'Edit ticket' : 'Details'}</h2>

            {editable ? (
              <form className="form" style={{ marginTop: 0 }} onSubmit={saveChanges} noValidate>
                <Field id="subject" label="Subject" type="text"
                       value={form.subject} onChange={set('subject')} error={errors.subject} />

                <div className="field-row">
                  <SelectField id="category" label="Category"
                               options={categories.map(c => ({ value: c, label: c }))}
                               value={form.category} onChange={set('category')} error={errors.category} />
                  <SelectField id="priority" label="Priority" options={PRIORITIES}
                               value={form.priority} onChange={set('priority')} error={errors.priority} />
                </div>

                <div className="field">
                  <label htmlFor="description">Description</label>
                  <textarea id="description" name="description" rows={6}
                            value={form.description} onChange={set('description')} />
                  <p className="field-error">{errors.description || ''}</p>
                </div>

                <div className="btn-row">
                  <button type="submit" className="btn btn--primary" disabled={busy === 'save'}>
                    {busy === 'save' ? 'Saving…' : 'Save changes'}
                  </button>
                </div>
              </form>
            ) : (
              <p className="section-note" style={{ marginTop: 0, whiteSpace: 'pre-wrap' }}>{ticket.description}</p>
            )}
          </section>

          <section className="section">
            <h2>Attachments</h2>

            {attachments.length === 0 && <p className="empty">No files attached yet.</p>}

            {attachments.map(att => (
              <div className="pref" key={att.id}>
                <div className="pref__text">
                  <strong>{att.fileName}</strong>
                  <span>{Math.round((att.fileSize || 0) / 1024)} KB · {formatDateTime(att.uploadedAt)}</span>
                </div>
                <div className="row-side">
                  <a className="text-link" href={API.ticketAttachment(id, att.id)} target="_blank" rel="noreferrer">Download</a>
                  <button type="button" className="btn btn--ghost" disabled={busy === 'attachment-' + att.id}
                          onClick={() => deleteAttachment(att.id)}>Remove</button>
                </div>
              </div>
            ))}

            <div className="btn-row" style={{ marginTop: 18 }}>
              <label className="btn btn--ghost">
                {busy === 'upload' ? 'Uploading…' : 'Attach a file'}
                <input type="file" hidden onChange={uploadFile} disabled={busy === 'upload'} />
              </label>
            </div>
          </section>
        </div>
      </main>
    </div>
  )
}
