import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
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

const PRIORITIES = [
  { value: '', label: 'Any priority' },
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'URGENT', label: 'Urgent' }
]

const PAGE_SIZE = 20

export default function TicketList() {
  const [categories, setCategories] = useState([])
  const [filters, setFilters] = useState({ status: '', priority: '', category: '', keyword: '' })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)   // Page<TicketResponse>
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    request(API.ticketCategories).then(r => { if (r.ok) setCategories(r.data || []) })
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true); setError('')

    request(withQuery(API.ticketSearch, { ...filters, page, size: PAGE_SIZE })).then(r => {
      if (cancelled) return
      if (r.ok) setResult(r.data)
      else setError('We could not load your tickets.')
      setLoading(false)
    }).catch(() => { if (!cancelled) { setError('Could not reach the server.'); setLoading(false) } })

    return () => { cancelled = true }
  }, [filters, page])

  function updateFilter(name) {
    return event => { setPage(0); setFilters(current => ({ ...current, [name]: event.target.value })) }
  }

  const tickets = result?.content || []

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head">
            <h1>My tickets</h1>
          </div>

          <div className="btn-row" style={{ margin: '18px 0 0' }}>
            <Link className="btn btn--primary" to="/tickets/new">Submit a ticket</Link>
          </div>

          <section className="section">
            <h2>Filter</h2>
            <div className="field-row">
              <SelectField id="status" label="Status" options={STATUSES}
                           value={filters.status} onChange={updateFilter('status')} />
              <SelectField id="priority" label="Priority" options={PRIORITIES}
                           value={filters.priority} onChange={updateFilter('priority')} />
            </div>
            <div className="field-row">
              <SelectField id="category" label="Category"
                           options={[{ value: '', label: 'Any category' }, ...categories.map(c => ({ value: c, label: c }))]}
                           value={filters.category} onChange={updateFilter('category')} />
              <Field id="keyword" label="Search" type="text" placeholder="Subject or description"
                     value={filters.keyword} onChange={updateFilter('keyword')} />
            </div>
          </section>

          <section className="section">
            <h2>Results</h2>

            {error && <Notice kind="error">{error}</Notice>}

            {!error && loading && <p className="empty">Loading tickets…</p>}

            {!error && !loading && tickets.length === 0 && (
              <p className="empty">No tickets match those filters.</p>
            )}

            {!error && !loading && tickets.length > 0 && (
              <div>
                {tickets.map(ticket => (
                  <Row key={ticket.id} to={'/tickets/' + ticket.id}
                       title={ticket.subject}
                       subtitle={ticket.category + ' · Opened ' + formatDateTime(ticket.createdAt)}
                       meta={<span>{ticket.priority}</span>}
                       right={<StatusPill value={ticket.status} />} />
                ))}
              </div>
            )}

            {result && result.totalPages > 1 && (
              <div className="btn-row" style={{ marginTop: 20 }}>
                <button type="button" className="btn btn--ghost" disabled={result.first}
                        onClick={() => setPage(p => Math.max(0, p - 1))}>Previous</button>
                <span className="hint">Page {result.number + 1} of {result.totalPages}</span>
                <button type="button" className="btn btn--ghost" disabled={result.last}
                        onClick={() => setPage(p => p + 1)}>Next</button>
              </div>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}
