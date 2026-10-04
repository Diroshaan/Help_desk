import { useEffect, useState } from 'react'
import { API, formatDateTime, request, withQuery } from '../../api.js'
import { BarList, Notice, SelectField, StatTile, humanize } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

/* Fixed list so a rating nobody gave still shows a row with zero. */
const RATINGS = [5, 4, 3, 2, 1]

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  // Feedback analytics for one chosen category, loaded separately.
  const [departments, setDepartments] = useState([])
  const [categories, setCategories] = useState([])
  const [category, setCategory] = useState('')
  const [summary, setSummary] = useState(null)
  const [summaryError, setSummaryError] = useState('')
  const [summaryLoading, setSummaryLoading] = useState(false)

  useEffect(() => {
    request(API.adminDashboard).then(r => {
      if (r.ok) setData(r.data)
      else setError('We could not load the dashboard.')
      setLoading(false)
    }).catch(() => { setError('Could not reach the server.'); setLoading(false) })
  }, [])

  useEffect(() => {
    request(API.departments).then(r => { if (r.ok) setDepartments(r.data || []) }).catch(() => {})
  }, [])

  useEffect(() => {
    request(API.categories).then(r => {
      if (r.ok) setCategories(r.data)
    }).catch(() => { /* picker stays empty */ })
  }, [])

  useEffect(() => {
    if (!category) { setSummary(null); setSummaryError(''); return }
    setSummaryLoading(true); setSummaryError('')
    request(withQuery(API.feedbackSummary, { category })).then(r => {
      if (r.ok) setSummary(r.data)
      else { setSummary(null); setSummaryError('We could not load feedback for that category.') }
      setSummaryLoading(false)
    }).catch(() => { setSummaryError('Could not reach the server.'); setSummaryLoading(false) })
  }, [category])

  function departmentName(code) {
    if (!code || code === 'UNASSIGNED' || code === 'null') return 'Not routed'
    return departments.find(d => d.code === code)?.name || code
  }
  // under an hour, show minutes
  const hours = data?.averageResolutionHours
  const averageTime = typeof hours !== 'number' ? null
    : hours < 1 ? { value: Math.max(1, Math.round(hours * 60)), unit: 'min' }
    : { value: Math.round(hours * 10) / 10, unit: 'h' }

  // open + in progress, routed or not
  const openBacklog = data
    ? (data.ticketsByStatus?.OPEN || 0) + (data.ticketsByStatus?.IN_PROGRESS || 0)
    : null

  const breakdown = summary?.ratingBreakdown || {}
  const peak = Math.max(1, ...RATINGS.map(r => breakdown[r] || 0))
  const responses = summary?.totalCount || 0

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Dashboard</h1></div>

          {error && <Notice kind="error" style={{ marginTop: 18 }}>{error}</Notice>}
          {!error && loading && <p className="empty">Loading…</p>}

          {!error && data && (
            <>
              <section className="section">
                <h2>Overview</h2>
                <div className="stats">
                  <StatTile label="Total tickets" value={data.totalTickets} />
                  <StatTile label="Open backlog" value={openBacklog} note="Open or in progress" />
                  <StatTile label="Average resolution time"
                            value={averageTime ? averageTime.value : null}
                            suffix={averageTime ? averageTime.unit : null}
                            note={averageTime ? 'From submitted to resolved' : 'No resolved tickets yet'} />
                  <StatTile label="SLA breaches" value={data.slaBreaches} note="Past their priority's deadline" />
                  <StatTile label="Active users" value={data.activeUsers} note={'of ' + data.totalUsers + ' accounts'} />
                </div>
                <p className="hint" style={{ marginTop: 12 }}>Updated {formatDateTime(data.generatedAt)}</p>
              </section>

              <section className="section">
                <h2>Tickets by status</h2>
                <BarList items={Object.entries(data.ticketsByStatus || {})
                  .map(([status, count]) => ({ key: status, label: humanize(status), value: count }))
                  .sort((a, b) => b.value - a.value)}
                  empty="No tickets yet." />
              </section>

              <section className="section">
                <h2>Tickets by department</h2>
                <BarList items={Object.entries(data.ticketsByDepartment || {})
                  .map(([code, count]) => ({ key: code, label: departmentName(code), value: count }))
                  .sort((a, b) => b.value - a.value)}
                  empty="No tickets have been routed to a department yet." />
              </section>

              <section className="section">
                <h2>Open backlog by department</h2>
                <BarList items={Object.entries(data.openBacklogByDepartment || {})
                  .map(([code, count]) => ({ key: code, label: departmentName(code), value: count }))
                  .sort((a, b) => b.value - a.value)}
                  empty="No open backlog right now." />
              </section>
            </>
          )}

          {/* Outside the {data && ...} block: it has its own endpoint, so it still
              works if the main dashboard fails to load. */}
          <section className="section">
            <h2>Feedback by category</h2>
            <p className="section-note">
              What students thought of the answers they received. Pick a category to see its ratings.
            </p>

            <SelectField
              id="feedbackCategory"
              label="Category"
              value={category}
              onChange={e => setCategory(e.target.value)}
              options={[
                { value: '', label: 'Choose a category…' },
                ...categories.map(c => ({ value: c.name, label: c.name + ' · ' + c.departmentName }))
              ]}
            />

            {summaryError && <Notice kind="error" style={{ marginTop: 16 }}>{summaryError}</Notice>}
            {!summaryError && summaryLoading && <p className="empty">Loading…</p>}
            {!summaryError && !summaryLoading && !category && (
              <p className="empty">No category chosen yet.</p>
            )}

            {!summaryError && !summaryLoading && category && summary && responses === 0 && (
              <p className="empty">Nobody has rated an answer in this category yet.</p>
            )}

            {!summaryError && !summaryLoading && category && summary && responses > 0 && (
              <>
                <div className="detail-list" style={{ marginTop: 16 }}>
                  <div>
                    <dt>Average rating</dt>
                    <dd>{summary.averageRating.toFixed(1)} out of 5</dd>
                  </div>
                  <div>
                    <dt>Responses</dt>
                    <dd>{responses}</dd>
                  </div>
                </div>

                <div className="bars">
                  {RATINGS.map(rating => {
                    const count = breakdown[rating] || 0
                    const share = Math.round((count / responses) * 100)
                    return (
                      <div className="bar-row" key={rating}>
                        <span className="bar-row__label">{rating} star{rating === 1 ? '' : 's'}</span>
                        {/* hover title shows the percentage */}
                        <div className="bar-row__track" title={count + ' of ' + responses + ' (' + share + '%)'}>
                          <div className="bar-row__fill" style={{ width: (count / peak) * 100 + '%' }} />
                        </div>
                        <span className="bar-row__value mono">{count}</span>
                      </div>
                    )
                  })}
                </div>
              </>
            )}
          </section>
        </div>
      </main>
    </div>
  )
}
