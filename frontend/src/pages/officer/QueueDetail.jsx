import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API, errorMessage, formatDateTime, request, requestForm } from '../../api.js'
import { Notice, StatusPill } from '../../components/Bits.jsx'
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
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

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
  }

  useEffect(() => { load() }, [id])

  async function startWorking() {
    setBusy('status'); setNotice(null)
    const result = await request(API.queueStatus(id), { method: 'PUT', body: { status: 'IN_PROGRESS' } })
    if (result.ok) setDetail(current => ({ ...current, ticket: result.data }))
    else setNotice({ kind: 'error', text: errorMessage(result, 'We could not update the status.') })
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
      // Resolving posts the resolution; the ticket status itself moves to
      // RESOLVED server-side, so refresh the ticket half of the view too.
      const ticketResult = await request(API.queueTicket(id))
      if (ticketResult.ok) setDetail(ticketResult.data)
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
      const ticketResult = await request(API.queueTicket(id))
      if (ticketResult.ok) setDetail(ticketResult.data)
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
              <div><dt>Priority</dt><dd>{ticket.priority}</dd></div>
              <div><dt>Submitted</dt><dd>{formatDateTime(ticket.createdAt)}</dd></div>
              <div><dt>Description</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{ticket.description}</dd></div>
            </div>

            {ticket.status === 'OPEN' && (
              <div className="btn-row" style={{ marginTop: 20 }}>
                <button type="button" className="btn btn--primary" disabled={busy === 'status'} onClick={startWorking}>
                  {busy === 'status' ? 'Updating…' : 'Start working on this ticket'}
                </button>
              </div>
            )}
          </section>

          <section className="section">
            <h2>Assignment</h2>
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
                {user && (
                  <button type="button" className="btn btn--ghost"
                          onClick={() => setAssignOfficerId(String(user.id))}>
                    Assign to me
                  </button>
                )}
              </div>
            </form>
          </section>

          <section className="section">
            <h2>Resolution</h2>

            <form className="form" style={{ marginTop: 0 }} onSubmit={saveResolution}>
              <div className="field">
                <label htmlFor="responseText">Response to the student</label>
                <textarea id="responseText" rows={5} value={responseText}
                          onChange={e => setResponseText(e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="resolutionFile">Attachment (optional)</label>
                <input id="resolutionFile" type="file" onChange={e => setAttachment(e.target.files?.[0] || null)} />
              </div>
              {resolution?.attachmentFileName && (
                <p className="hint">Current attachment: {resolution.attachmentFileName}</p>
              )}
              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'resolution'}>
                  {busy === 'resolution' ? 'Saving…' : resolution ? 'Update resolution' : 'Post resolution'}
                </button>
                {resolution && (
                  <button type="button" className="btn btn--danger" disabled={busy === 'revoke'} onClick={revokeResolution}>
                    Revoke resolution
                  </button>
                )}
              </div>
            </form>
          </section>

          <section className="section">
            <h2>Internal notes</h2>
            <p className="section-note">Visible to officers and administrators only, never to the student.</p>

            {notes.length === 0 && <p className="empty">No notes yet.</p>}

            {notes.map(note => (
              <div className="pref" key={note.id}>
                <div className="pref__text">
                  <strong>{note.note}</strong>
                  <span>Officer #{note.officerId} · {formatDateTime(note.createdAt)}</span>
                </div>
                <button type="button" className="btn btn--ghost" disabled={busy === 'note-' + note.id}
                        onClick={() => deleteNote(note.id)}>Remove</button>
              </div>
            ))}

            <form className="btn-row" style={{ marginTop: 18 }} onSubmit={addNote}>
              <input type="text" placeholder="Add an internal note" value={noteText}
                     onChange={e => setNoteText(e.target.value)} style={{ flex: 1, minWidth: 220 }} />
              <button type="submit" className="btn btn--ghost" disabled={busy === 'note'}>Add note</button>
            </form>
          </section>
        </div>
      </main>
    </div>
  )
}
