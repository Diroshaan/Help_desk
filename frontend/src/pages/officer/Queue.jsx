import { useEffect, useState } from 'react'
import { API, formatDateTime, request, withQuery } from '../../api.js'
import { Field, Notice, Row, SelectField, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const STATUSES = [
  { value: '', label: 'Any status' },
  { value: 'OPEN', label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'RESOLVED', label: 'Resolved' },
  { value: 'WITHDRAWN', label: 'Withdrawn' }
]

export default function Queue() {
  const [departments, setDepartments] = useState([])
  const [status, setStatus] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [studentId, setStudentId] = useState('')
  const [tickets, setTickets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    request(API.departments).then(r => { if (r.ok) setDepartments(r.data || []) })
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true); setError('')

    request(withQuery(API.queue, {
      departmentId: departmentId || undefined,
      status: status || undefined,
      studentId: studentId || undefined
    })).then(r => {
      if (cancelled) return
      if (r.ok) setTickets(r.data)
      else setError('We could not load the queue.')
      setLoading(false)
    }).catch(() => { if (!cancelled) { setError('Could not reach the server.'); setLoading(false) } })

    return () => { cancelled = true }
  }, [status, departmentId, studentId])

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Support queue</h1></div>

          <section className="section">
            <h2>Filter</h2>
            <div className="field-row">
              <SelectField id="departmentId" label="Department"
                           options={[{ value: '', label: 'Any department' }, ...departments.map(d => ({ value: d.code, label: d.name }))]}
                           value={departmentId} onChange={e => setDepartmentId(e.target.value)} />
              <SelectField id="status" label="Status" options={STATUSES}
                           value={status} onChange={e => setStatus(e.target.value)} />
            </div>
            <Field id="studentId" label="Or find one student's tickets by student record ID" type="number"
                   value={studentId} onChange={e => setStudentId(e.target.value)}
                   hint="Entering a student ID here overrides the filters above." />
          </section>

          <section className="section">
            <h2>Tickets</h2>

            {error && <Notice kind="error">{error}</Notice>}
            {!error && loading && <p className="empty">Loading the queue…</p>}
            {!error && !loading && tickets.length === 0 && <p className="empty">Nothing matches those filters.</p>}

            {!error && !loading && tickets.map(ticket => (
              <Row key={ticket.id} to={'/queue/' + ticket.id}
                   title={ticket.subject}
                   subtitle={ticket.category + ' · Student #' + ticket.studentId + ' · Opened ' + formatDateTime(ticket.createdAt)}
                   meta={<span>{ticket.priority}</span>}
                   right={<StatusPill value={ticket.status} />} />
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
