import { useEffect, useState } from 'react'
import { API, formatDateTime, request, withQuery } from '../../api.js'
import { Notice, SelectField } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

/* The five ratings, highest first.

   Descending because that is how a reader scans a rating breakdown: the top
   row is the best possible score and the eye travels down towards the
   complaints. Fixed as a constant rather than read from the response, so a
   rating nobody has given still gets a row showing zero - an absent row would
   read as "no data" when the truth is "nobody chose it". */
const RATINGS = [5, 4, 3, 2, 1]

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  // Feedback analytics (#47). Kept in its own state rather than folded into
  // the dashboard payload: it answers a question about ONE category the
  // administrator picks, so it cannot be part of a single system-wide summary.
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
    request(API.categories).then(r => {
      if (r.ok) setCategories(r.data)
    }).catch(() => { /* the picker stays empty; the rest of the page still works */ })
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

  // The longest bar fills the track and the rest are drawn in proportion to
  // it. Scaling to the largest count rather than to the total is what keeps a
  // breakdown readable when one rating dominates: against the total, four of
  // the five bars would be slivers.
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
                <div className="detail-list">
                  <div><dt>Total tickets</dt><dd>{data.totalTickets}</dd></div>
                  <div><dt>SLA breaches</dt><dd>{data.slaBreaches}</dd></div>
                  <div><dt>Total users</dt><dd>{data.totalUsers}</dd></div>
                  <div><dt>Active users</dt><dd>{data.activeUsers}</dd></div>
                  <div><dt>Generated</dt><dd>{formatDateTime(data.generatedAt)}</dd></div>
                </div>
              </section>

              <section className="section">
                <h2>Tickets by status</h2>
                {Object.entries(data.ticketsByStatus || {}).map(([status, count]) => (
                  <div className="pref" key={status}>
                    <div className="pref__text"><strong>{status}</strong></div>
                    <div className="row-side"><span className="mono">{count}</span></div>
                  </div>
                ))}
              </section>

              <section className="section">
                <h2>Tickets by department</h2>
                {Object.entries(data.ticketsByDepartment || {}).map(([code, count]) => (
                  <div className="pref" key={code}>
                    <div className="pref__text"><strong>{code}</strong></div>
                    <div className="row-side"><span className="mono">{count}</span></div>
                  </div>
                ))}
              </section>

              <section className="section">
                <h2>Open backlog by department</h2>
                {Object.entries(data.openBacklogByDepartment || {}).length === 0 && (
                  <p className="empty">No open backlog right now.</p>
                )}
                {Object.entries(data.openBacklogByDepartment || {}).map(([code, count]) => (
                  <div className="pref" key={code}>
                    <div className="pref__text"><strong>{code}</strong></div>
                    <div className="row-side"><span className="mono">{count}</span></div>
                  </div>
                ))}
              </section>
            </>
          )}

          {/* Feedback analytics (#47).

              Outside the {data && ...} block on purpose: it has its own
              endpoint and its own failure mode, so a dashboard that could not
              load should not also hide the feedback tool, and a category with
              no feedback should not look like a broken dashboard. */}
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
                    {/* One decimal. Two would claim a precision that a handful
                        of whole-number ratings does not have. */}
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
                        {/* The title is the hover layer: it carries the share,
                            which the row does not already show. Repeating the
                            count there would be noise. */}
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
