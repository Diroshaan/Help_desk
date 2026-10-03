import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API, UPLOAD_ACCEPT, errorMessage, formatBytes, formatDateTime, request, requestForm, uploadProblem } from '../../api.js'
import { ConfirmButton, Notice, StatusPill, StatusTimeline, humanize } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'
import { useSession } from '../../hooks/useSession.jsx'

export default function QueueDetail() {
  const { id } = useParams()
  const { user } = useSession()

  const [detail, setDetail] = useState(null)     // TicketQueueDetailResponse
  const [departments, setDepartments] = useState([])
  const [assignDept, setAssignDept] = useState('')
  const [assignOfficerId, setAssignOfficerId] = useState('')
  const [noteText, setNoteText] = useState('')
  const [responseText, setResponseText] = useState('')
  const [attachment, setAttachment] = useState(null)
  const [fileKey, setFileKey] = useState(0)        // bumping it empties the file input
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  // The student's files (F4 #40) and the status timeline (F4, from F2's
  // history). null means the endpoint did not answer - shown as a short note
  // rather than as "no files", which would be a different, false statement.
  const [files, setFiles] = useState(null)
  const [history, setHistory] = useState(null)

  async function load() {
    setLoading(true)
    const [detailResult, deptResult] = await Promise.all([
      request(API.queueTicket(id)),
      request(API.departments)
    ])

    if (!detailResult.ok) { setNotFound(true); setLoading(false); return }

    setDetail(detailResult.data)
    setAssignDept(detailResult.data.ticket.assignedDepartmentId || '')
    setAssignOfficerId(detailResult.data.ticket.assignedOfficerId || '')
    setResponseText(detailResult.data.resolution?.responseText || '')
    if (deptResult.ok) setDepartments(deptResult.data)
    setLoading(false)
    loadExtras()
  }

  async function loadExtras() {
    const [fileResult, historyResult] = await Promise.all([
      request(API.queueAttachments(id)),
      request(API.queueHistory(id))
    ])
    setFiles(fileResult.ok && Array.isArray(fileResult.data) ? fileResult.data : null)
    setHistory(historyResult.ok && Array.isArray(historyResult.data) ? historyResult.data : null)
  }

  useEffect(() => { load() }, [id])

  async function startWorking() {
    setBusy('status'); setNotice(null)
    const result = await request(API.queueStatus(id), { method: 'PUT', body: { status: 'IN_PROGRESS' } })
    if (result.ok) {
      setDetail(current => ({ ...current, ticket: result.data }))
      setAssignOfficerId(result.data.assignedOfficerId || '')
      setNotice({ kind: 'info', text: 'You picked this ticket up. The student has been told it is being worked on.' })
      loadExtras()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not update the status.') })
    }
    setBusy(null)
  }

  async function saveAssignment(event) {
    event.preventDefault()
    setBusy('assign'); setNotice(null)
    const result = await request(API.queueAssign(id), {
      method: 'PUT',
      body: { departmentId: assignDept || null, officerId: assignOfficerId ? Number(assignOfficerId) : null }
    })
    if (result.ok) {
      setDetail(current => ({ ...current, ticket: result.data }))
      // The server can resolve a department the caller never typed (a target
      // officer who serves exactly one), so the form has to reflect what was
      // actually saved, not just what was submitted.
      setAssignDept(result.data.assignedDepartmentId || '')
      setAssignOfficerId(result.data.assignedOfficerId || '')
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not assign this ticket.') })
    }
    setBusy(null)
  }

  async function addNote(event) {
    event.preventDefault()
    if (!noteText.trim()) return
    setBusy('note'); setNotice(null)
    const result = await request(API.queueNotes(id), { method: 'POST', body: { note: noteText.trim() } })
    if (result.ok) {
      setDetail(current => ({ ...current, notes: [...current.notes, result.data] }))
      setNoteText('')
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not add that note.') })
    }
    setBusy(null)
  }

  async function deleteNote(noteId) {
    setBusy('note-' + noteId)
    const result = await request(API.queueNote(id, noteId), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setDetail(current => ({ ...current, notes: current.notes.filter(n => n.id !== noteId) }))
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not remove that note.') })
    }
    setBusy(null)
  }

  async function saveResolution(event) {
    event.preventDefault()
    if (!responseText.trim()) return
    setBusy('resolution'); setNotice(null)

    const form = new FormData()
    form.append('responseText', responseText.trim())
    if (attachment) form.append('attachment', attachment)

    const editing = Boolean(detail.resolution)
    const result = await requestForm(API.queueResolution(id), { method: editing ? 'PUT' : 'POST', form })

    if (result.ok) {
      setDetail(current => ({ ...current, resolution: result.data }))
      setAttachment(null)
      setFileKey(k => k + 1)
      setNotice({ kind: 'info', text: editing ? 'Your answer has been updated.' : 'Your answer has been posted and the ticket is resolved.' })
      // Resolving posts the resolution; the ticket status itself moves to
      // RESOLVED server-side, so refresh the ticket half of the view too.
      const ticketResult = await request(API.queueTicket(id))
      if (ticketResult.ok) setDetail(ticketResult.data)
      loadExtras()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not save the resolution.') })
    }
    setBusy(null)
  }

  async function revokeResolution() {
    setBusy('revoke'); setNotice(null)
    const result = await request(API.queueResolution(id), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setDetail(current => ({ ...current, resolution: null }))
      setResponseText('')
      setNotice({ kind: 'info', text: 'The answer was revoked and the ticket is back in progress.' })
      const ticketResult = await request(API.queueTicket(id))
      if (ticketResult.ok) setDetail(ticketResult.data)
      loadExtras()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not revoke the resolution.') })
    }
    setBusy(null)
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
        <div className="btn-row" style={{ marginTop: 20 }}>
          <Link className="btn btn--ghost" to="/queue">Back to the queue</Link>
        </div>
      </div>
    </main></div>
  }

  const { ticket, resolution, notes } = detail
  // Nothing is re-routed or answered once a ticket is finished (F4 rule).
  const closed = ticket.status === 'RESOLVED' || ticket.status === 'WITHDRAWN'
  const mine = user && ticket.assignedOfficerId === user.id

  function chooseFile(event) {
    const file = event.target.files?.[0] || null
    const problem = uploadProblem(file)
    if (problem) {
      event.target.value = ''
      setAttachment(null)
      setNotice({ kind: 'error', text: problem })
      return
    }
    setAttachment(file)
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <Link className="back-link" to="/queue">&larr; Back to the queue</Link>

          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>{ticket.subject}</h1>
            <StatusPill value={ticket.status} />
          </div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <div className="detail-list">
              <div><dt>Student</dt><dd className="mono">#{ticket.studentId}</dd></div>
              <div><dt>Category</dt><dd>{ticket.category}</dd></div>
              <div><dt>Priority</dt><dd>{humanize(ticket.priority)}</dd></div>
              <div><dt>Assigned to</dt><dd>
                {ticket.assignedOfficerId
                  ? (mine ? <span className="tag">You</span> : 'Officer #' + ticket.assignedOfficerId)
                  : 'Nobody yet'}
                {ticket.assignedDepartmentId ? ' · ' + (departments.find(d => d.code === ticket.assignedDepartmentId)?.name || ticket.assignedDepartmentId) : ' · Not routed'}
              </dd></div>
              <div><dt>Submitted</dt><dd>{formatDateTime(ticket.createdAt)}</dd></div>
              <div><dt>Description</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{ticket.description}</dd></div>
            </div>

            {ticket.status === 'OPEN' && (
              <div className="btn-row" style={{ marginTop: 20 }}>
                <button type="button" className="btn btn--primary" disabled={busy === 'status'} onClick={startWorking}>
                  {busy === 'status' ? 'Updating…' : 'Pick up and start working'}
                </button>
              </div>
            )}
          </section>

          {/* F4 #40: officers can now open what the student attached. */}
          <section className="section">
            <h2>Student's files</h2>
            {files === null && <p className="empty">The student's files could not be loaded.</p>}
            {files && files.length === 0 && <p className="empty">The student did not attach any files.</p>}
            {files && files.map(file => (
              <div className="pref" key={file.id}>
                <div className="pref__text">
                  <strong>{file.fileName}</strong>
                  <span>{formatBytes(file.fileSize || 0)} · {formatDateTime(file.uploadedAt)}</span>
                </div>
                <div className="row-side">
                  <a className="text-link" href={API.queueAttachment(id, file.id)} target="_blank" rel="noreferrer">Open</a>
                </div>
              </div>
            ))}
          </section>

          <section className="section">
            <h2>Status timeline</h2>
            {history === null
              ? <p className="empty">The timeline could not be loaded.</p>
              : <StatusTimeline entries={history} />}
          </section>

          <section className="section">
            <h2>Assignment</h2>
            {closed ? (
              <p className="section-note" style={{ marginTop: 0 }}>
                This ticket is {humanize(ticket.status).toLowerCase()}, so it can no longer be re-routed.
              </p>
            ) : (
            <form className="form" style={{ marginTop: 0 }} onSubmit={saveAssignment}>
              <div className="field-row">
                <div className="field">
                  <label htmlFor="assignDept">Department</label>
                  <select id="assignDept" value={assignDept} onChange={e => setAssignDept(e.target.value)}>
                    <option value="">Unassigned</option>
                    {departments.map(d => <option key={d.code} value={d.code}>{d.name}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor="assignOfficerId">Officer ID</label>
                  <input id="assignOfficerId" type="number" value={assignOfficerId}
                         onChange={e => setAssignOfficerId(e.target.value)} placeholder="Officer record ID" />
                </div>
              </div>
              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'assign'}>
                  {busy === 'assign' ? 'Saving…' : 'Save assignment'}
                </button>
                {user && !mine && (
                  <button type="button" className="btn btn--ghost"
                          onClick={() => setAssignOfficerId(String(user.id))}>
                    Assign to me
                  </button>
                )}
              </div>
            </form>
            )}
          </section>

          {ticket.status === 'WITHDRAWN' ? (
            <section className="section">
              <h2>Resolution</h2>
              <p className="section-note" style={{ marginTop: 0 }}>The student withdrew this ticket, so it does not need an answer.</p>
            </section>
          ) : (
          <section className="section">
            <h2>Resolution</h2>
            {ticket.status === 'OPEN' && !resolution && (
              <p className="section-note" style={{ marginTop: 0 }}>Pick the ticket up first, then post your answer here.</p>
            )}

            <form className="form" style={{ marginTop: 0 }} onSubmit={saveResolution}>
              <div className="field">
                <label htmlFor="responseText">Response to the student</label>
                <textarea id="responseText" rows={5} value={responseText} maxLength={4000}
                          placeholder="What you did, and what the student needs to do next."
                          onChange={e => setResponseText(e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="resolutionFile">Attachment (optional)</label>
                <input id="resolutionFile" key={fileKey} type="file" accept={UPLOAD_ACCEPT} onChange={chooseFile} />
                <p className="hint">PDF or image, up to 5 MB. The student can download it with your answer.</p>
              </div>
              {resolution?.attachmentFileName && (
                <a className="file-link" href={API.queueResolutionFile(id)} target="_blank" rel="noreferrer">
                  {resolution.attachmentFileName}
                  {resolution.attachmentFileSize ? <span>{formatBytes(resolution.attachmentFileSize)}</span> : null}
                </a>
              )}
              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'resolution' || !responseText.trim()}>
                  {busy === 'resolution' ? 'Saving…' : resolution ? 'Update answer' : 'Post answer and resolve'}
                </button>
                {resolution && (
                  <ConfirmButton label="Revoke answer" confirmLabel="Yes, revoke"
                                 question="Revoke this answer? The ticket goes back to in progress."
                                 busy={busy === 'revoke'} onConfirm={revokeResolution} />
                )}
              </div>
            </form>
          </section>
          )}

          <section className="section">
            <h2>Internal notes</h2>
            <p className="section-note">Visible to officers and administrators only, never to the student.</p>

            {notes.length === 0 && <p className="empty">No notes yet.</p>}

            {notes.map(note => (
              <div className="pref" key={note.id}>
                <div className="pref__text">
                  <strong>{note.note}</strong>
                  <span>{user && note.officerId === user.id ? 'You' : 'Officer #' + note.officerId} · {formatDateTime(note.createdAt)}</span>
                </div>
                <button type="button" className="btn btn--ghost" disabled={busy === 'note-' + note.id}
                        onClick={() => deleteNote(note.id)}>Remove</button>
              </div>
            ))}

            <form className="btn-row" style={{ marginTop: 18 }} onSubmit={addNote}>
              <input type="text" placeholder="Add an internal note" value={noteText} maxLength={2000}
                     onChange={e => setNoteText(e.target.value)} style={{ flex: 1, minWidth: 220 }} />
              <button type="submit" className="btn btn--ghost" disabled={busy === 'note'}>Add note</button>
            </form>
          </section>
        </div>
      </main>
    </div>
  )
}
