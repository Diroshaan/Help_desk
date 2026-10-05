import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API, UPLOAD_ACCEPT, errorMessage, fieldErrors, formatBytes, formatDateTime, request, requestForm, uploadProblem } from '../../api.js'
import { ConfirmButton, Field, Notice, Rating, SelectField, StatusPill, StatusTimeline, humanize } from '../../components/Bits.jsx'
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
  const [bookmark, setBookmark] = useState(null)

  // `feedback` is what the server has saved; `feedbackForm` is the draft being typed.
  const [feedback, setFeedback] = useState(null)
  const [feedbackForm, setFeedbackForm] = useState({ rating: 0, comment: '' })

  const [archived, setArchived] = useState(false)

  // `answer` stays null until the ticket is resolved (404 = no answer yet)
  const [history, setHistory] = useState(null)
  const [answer, setAnswer] = useState(null)

  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  async function load() {
    setLoading(true)
    const [ticketResult, attachmentResult, bookmarkResult, categoryResult, historyResult, archivedResult] = await Promise.all([
      request(API.ticket(id)),
      request(API.ticketAttachments(id)),
      request(API.bookmarks),
      request(API.ticketCategories),
      request(API.ticketHistory(id)),
      request(API.ticketsArchived)
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
    setHistory(historyResult.ok ? historyResult.data : null)
    setArchived(archivedResult.ok && Array.isArray(archivedResult.data)
      ? archivedResult.data.some(t => t.id === Number(id))
      : false)

    if (bookmarkResult.ok) {
      const existing = (bookmarkResult.data || []).find(b => b.ticketId === Number(id))
      setBookmark(existing || null)
    }

    // Answer and feedback only exist for RESOLVED tickets. A 404 on feedback
    // just means the student hasn't rated it yet.
    if (ticketResult.data.status === 'RESOLVED') {
      const answerResult = await request(API.ticketResolution(id))
      setAnswer(answerResult.ok ? answerResult.data : null)

      const feedbackResult = await request(API.ticketFeedback(id))
      if (feedbackResult.ok && feedbackResult.data) {
        setFeedback(feedbackResult.data)
        setFeedbackForm({
          rating: feedbackResult.data.rating,
          comment: feedbackResult.data.comment || ''
        })
      }
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
      } else if (result.status === 409) {
        // 409: an officer changed the ticket meanwhile, so reload instead of overwriting
        await load()
        setNotice({ kind: 'error', text: 'An officer updated this ticket while you were editing it. The latest version is shown below; make your change again.' })
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
        const historyResult = await request(API.ticketHistory(id))
        if (historyResult.ok) setHistory(historyResult.data)
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

    const problem = uploadProblem(file)
    if (problem) { setNotice({ kind: 'error', text: problem }); return }

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

  /**
   * Submits feedback (POST) or updates it (PUT); the body is the same. The
   * rating is checked here so the error shows under the stars.
   */
  async function saveFeedback(event) {
    event.preventDefault()
    setNotice(null); setErrors({})

    if (!feedbackForm.rating) {
      setErrors({ rating: 'Choose a rating from one to five stars.' })
      return
    }

    setBusy('feedback')
    try {
      const result = await request(API.ticketFeedback(id), {
        method: feedback ? 'PUT' : 'POST',
        body: { rating: feedbackForm.rating, comment: feedbackForm.comment.trim() || null }
      })

      if (result.ok) {
        setFeedback(result.data)
        setNotice({ kind: 'info', text: feedback ? 'Your feedback has been updated.' : 'Thank you for your feedback.' })
      } else {
        const fields = fieldErrors(result, ['rating', 'comment'])
        if (Object.keys(fields).length) setErrors(fields)
        else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save your feedback.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  /**
   * Archive or unarchive a finished ticket. Archiving only hides it from the
   * student's active list; nothing is deleted.
   */
  async function toggleArchive() {
    setNotice(null); setBusy('archive')
    try {
      const result = await request(API.ticketArchive(id), { method: archived ? 'DELETE' : 'POST' })
      if (result.ok || result.status === 204) {
        setArchived(!archived)
        setNotice({
          kind: 'info',
          text: archived ? 'This ticket is back in your active list.' : 'This ticket has been archived.'
        })
      } else if (result.status === 409 && !archived) {
        // already archived, so just show that
        setArchived(true)
        setNotice({ kind: 'info', text: 'This ticket is already archived.' })
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not archive this ticket.') })
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
      setNotice(null)
      if (bookmark) {
        const result = await request(API.bookmark(bookmark.id), { method: 'DELETE' })
        if (result.ok || result.status === 204) setBookmark(null)
        else setNotice({ kind: 'error', text: errorMessage(result, 'We could not remove the bookmark.') })
      } else {
        const result = await request(API.bookmarks, { method: 'POST', body: { ticketId: Number(id) } })
        if (result.ok) setBookmark(result.data)
        else setNotice({ kind: 'error', text: errorMessage(result, 'We could not bookmark this ticket.') })
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
  // only finished tickets can be archived
  const archivable = ticket.status === 'RESOLVED' || ticket.status === 'WITHDRAWN'

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

          {/* the answer goes first once the ticket is resolved */}
          {answer && (
            <section className="section">
              <h2>The help desk's answer</h2>
              <div className="answer">
                <p className="answer__head">
                  <strong>{answer.officerName || 'Help desk officer'}</strong>
                  {' · '}{formatDateTime(answer.publishedAt || answer.createdAt)}
                  {answer.updatedAt && answer.publishedAt && answer.updatedAt !== answer.publishedAt
                    ? ' · edited ' + formatDateTime(answer.updatedAt) : ''}
                </p>
                <p className="answer__text">{answer.responseText}</p>
                {answer.attachmentFileName && (
                  <a className="file-link" href={API.ticketResolutionFile(id)} target="_blank" rel="noreferrer">
                    {answer.attachmentFileName}
                    {answer.attachmentFileSize ? <span>{formatBytes(answer.attachmentFileSize)}</span> : null}
                  </a>
                )}
              </div>
            </section>
          )}

          <section className="section">
            <div className="detail-list">
              <div><dt>Category</dt><dd>{ticket.category}</dd></div>
              <div><dt>Priority</dt><dd>{humanize(ticket.priority)}</dd></div>
              <div><dt>Submitted</dt><dd>{formatDateTime(ticket.createdAt)}</dd></div>
              <div><dt>Last updated</dt><dd>{formatDateTime(ticket.updatedAt)}</dd></div>
            </div>

            <div className="btn-row" style={{ marginTop: 20 }}>
              <button type="button" className="btn btn--ghost" onClick={toggleBookmark} disabled={busy === 'bookmark'}>
                {bookmark ? 'Remove bookmark' : 'Bookmark this ticket'}
              </button>
              {/* only resolved or withdrawn tickets can be archived */}
              {archivable && (
                <button type="button" className="btn btn--ghost" onClick={toggleArchive} disabled={busy === 'archive'}>
                  {archived ? 'Move back to active' : 'Archive this ticket'}
                </button>
              )}
              {withdrawable && (
                <ConfirmButton label="Withdraw ticket" confirmLabel="Yes, withdraw"
                               question="Withdraw this ticket? This cannot be undone."
                               busy={busy === 'withdraw'} onConfirm={withdraw} />
              )}
            </div>
          </section>

          <section className="section">
            <h2>{editable ? 'Edit ticket' : 'Details'}</h2>

            {editable ? (
              <form className="form" style={{ marginTop: 0 }} onSubmit={saveChanges} noValidate>
                <Field id="subject" label="Subject" type="text" maxLength={150}
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
                  <textarea id="description" name="description" rows={6} maxLength={2000}
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

          {/* every status change, who made it and when */}
          <section className="section">
            <h2>Status timeline</h2>
            {history === null
              ? <p className="empty">The timeline is not available right now.</p>
              : <StatusTimeline entries={history} />}
          </section>

          {/* rating only appears once the ticket is resolved */}
          {ticket.status === 'RESOLVED' && (
            <section className="section">
              <h2>{feedback ? 'Your feedback' : 'Rate this service'}</h2>
              <p className="section-note" style={{ marginTop: 0 }}>
                {feedback
                  ? 'You rated this ticket. You can change your rating or comment below.'
                  : 'Your ticket has been resolved. Tell the help desk how it went.'}
              </p>

              <form className="form" style={{ marginTop: 14 }} onSubmit={saveFeedback} noValidate>
                <div className="field">
                  <label htmlFor="rating">Rating</label>
                  <Rating
                    value={feedbackForm.rating}
                    onChange={star => setFeedbackForm(current => ({ ...current, rating: star }))}
                  />
                  <p className="field-error">{errors.rating || ''}</p>
                </div>

                <div className="field">
                  <label htmlFor="comment">Comment <span className="hint">(optional)</span></label>
                  <textarea id="comment" name="comment" rows={4}
                            maxLength={1000}
                            placeholder="What went well, or what could have been better?"
                            value={feedbackForm.comment}
                            onChange={e => setFeedbackForm(current => ({ ...current, comment: e.target.value }))} />
                  {/* same 1000-character limit as the server */}
                  <p className="field-error">{errors.comment || ''}</p>
                </div>

                <div className="btn-row">
                  <button type="submit" className="btn btn--primary" disabled={busy === 'feedback'}>
                    {busy === 'feedback' ? 'Saving…' : (feedback ? 'Update feedback' : 'Submit feedback')}
                  </button>
                </div>
              </form>

              {feedback && (
                <p className="foot-note" style={{ marginTop: 6 }}>
                  Submitted {formatDateTime(feedback.createdAt)}
                  {feedback.updatedAt && feedback.updatedAt !== feedback.createdAt
                    ? ' · last edited ' + formatDateTime(feedback.updatedAt)
                    : ''}
                </p>
              )}
            </section>
          )}

          <section className="section">
            <h2>Attachments</h2>

            {attachments.length === 0 && <p className="empty">No files attached.</p>}

            {attachments.map(att => (
              <div className="pref" key={att.id}>
                <div className="pref__text">
                  <strong>{att.fileName}</strong>
                  <span>{formatBytes(att.fileSize || 0)} · {formatDateTime(att.uploadedAt)}</span>
                </div>
                <div className="row-side">
                  <a className="text-link" href={API.ticketAttachment(id, att.id)} target="_blank" rel="noreferrer">Download</a>
                  {/* files can only be changed while the ticket is still open */}
                  {editable && (
                    <button type="button" className="btn btn--ghost" disabled={busy === 'attachment-' + att.id}
                            onClick={() => deleteAttachment(att.id)}>Remove</button>
                  )}
                </div>
              </div>
            ))}

            {editable ? (
              <div className="btn-row" style={{ marginTop: 18 }}>
                <label className="btn btn--ghost">
                  {busy === 'upload' ? 'Uploading…' : 'Attach a file'}
                  <input type="file" hidden accept={UPLOAD_ACCEPT} onChange={uploadFile} disabled={busy === 'upload'} />
                </label>
                <span className="hint">PDF or image, up to 5 MB.</span>
              </div>
            ) : (
              <p className="hint" style={{ marginTop: 12 }}>Files can be added only while the ticket is open.</p>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}
