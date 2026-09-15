import { useEffect, useState } from 'react'
import { API, formatDateTime, request } from '../../api.js'
import { Notice } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    request(API.adminDashboard).then(r => {
      if (r.ok) setData(r.data)
      else setError('We could not load the dashboard.')
      setLoading(false)
    }).catch(() => { setError('Could not reach the server.'); setLoading(false) })
  }, [])

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
        </div>
      </main>
    </div>
  )
}
