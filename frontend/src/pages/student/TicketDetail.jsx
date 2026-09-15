import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API, errorMessage, fieldErrors, formatDateTime, request, requestForm } from '../../api.js'
import { Field, Notice, Rating, SelectField, StatusPill } from '../../components/Bits.jsx'
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

  // F3, US-12 - "submit a review and feedback once my ticket is marked
  // RESOLVED". `feedback` is the saved FeedbackResponse, or null when this
  // ticket has none yet; `feedbackForm` is what the student is currently
  // editing. They are kept apart so a half-typed comment never looks like it
  // has been saved - the form only becomes the saved record when the server
  // says so.
  const [feedback, setFeedback] = useState(null)
  const [feedbackForm, setFeedbackForm] = useState({ rating: 0, comment: '' })

  // Whether this ticket has been archived IN THIS BROWSER SESSION.
  //
  // Honest limitation, worth knowing rather than hiding: the backend has
  // POST and DELETE for the archive but no GET, so there is no way to ask
  // "is this ticket archived?" on load. The flag therefore starts false on
  // every page load, and the button says "Archive" again even for a ticket
  // that is already archived - pressing it returns a clear "already archived"
  // message rather than doing damage. Adding GET /api/tickets/{id}/archive
  // would fix it properly; that is a backend change and is out of scope here.
  const [archived, setArchived] = useState(false)

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

    // Feedback is only fetched for a RESOLVED ticket, and only then because
    // FeedbackService rejects submission on anything else - asking for
    // feedback on an OPEN ticket would be a guaranteed 404 on every load of
    // every open ticket, which is noise in the log and a wasted round trip.
    //
    // A 404 here is the NORMAL case, not an error: it means "resolved, but
    // this student has not rated it yet", which is exactly the state the form
    // below exists to fill. Only a 200 sets the saved record.
    if (ticketResult.data.status === 'RESOLVED') {
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

  /**
   * Submit a new rating, or update the one already given (F3, US-12).
   *
   * One handler rather than two, because the difference between "submit" and
   * "update" is a single verb - POST when there is no saved feedback yet, PUT
   * when there is - and the backend takes the identical body either way. Two
   * near-identical functions would be two places to fix the next time the
   * payload changes.
   *
   * The rating is required client-side because FeedbackRequest declares it
   * @NotNull with a 1-5 range: catching an unset rating here gives an
   * immediate message under the stars, where the server's 400 would arrive
   * after a round trip with nothing pointing at the control at fault.
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
   * Move a resolved ticket out of the active history, or bring it back (F3).
   *
   * Archiving is not deleting: the ticket and everything on it stay exactly
   * where they are, and an archived_tickets row simply records that this
   * student no longer wants it in their working list. That is why the button
   * is a plain ghost button and not styled as a destructive action - nothing
   * is destroyed and the next line of code can undo it.
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
              {/* Archiving is offered only on a RESOLVED ticket because
                  TicketArchiveService rejects anything else. Showing a button
                  that is guaranteed to fail would be worse than not showing
                  one. */}
              {ticket.status === 'RESOLVED' && (
                <button type="button" className="btn btn--ghost" onClick={toggleArchive} disabled={busy === 'archive'}>
                  {archived ? 'Move back to active' : 'Archive this ticket'}
                </button>
              )}
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

          {/* F3, US-12 - rate the service once the ticket is RESOLVED.
              Hidden entirely on an OPEN or IN_PROGRESS ticket rather than
              shown disabled: a student has nothing to rate until the desk has
              actually answered, so an inert form would just be a question
              they cannot yet answer. */}
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
                  {/* maxLength matches @Column(length = 1000) on Feedback.comment.
                      The entity has no matching @Size, so an over-length comment
                      would reach MySQL and come back as a confusing "already in
                      use" error - stopping it in the textarea avoids that
                      entirely without touching the backend. */}
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
