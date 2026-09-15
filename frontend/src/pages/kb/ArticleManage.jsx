import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, formatDateTime, request, withQuery } from '../../api.js'
import { Notice, Row } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const PAGE_SIZE = 20

export default function ArticleManage() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true); setError('')

    request(withQuery(API.articlesManage, { page, size: PAGE_SIZE })).then(r => {
      if (cancelled) return
      if (r.ok) setResult(r.data)
      else setError('We could not load your articles.')
      setLoading(false)
    }).catch(() => { if (!cancelled) { setError('Could not reach the server.'); setLoading(false) } })

    return () => { cancelled = true }
  }, [page])

  const articles = result?.content || []

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>Manage articles</h1>
            <Link className="btn btn--primary" to="/kb/manage/new">New article</Link>
          </div>

          <section className="section">
            {error && <Notice kind="error">{error}</Notice>}
            {!error && loading && <p className="empty">Loading…</p>}
            {!error && !loading && articles.length === 0 && (
              <p className="empty">You have not written any articles yet.</p>
            )}

            {!error && !loading && articles.map(article => (
              <Row key={article.id} to={'/kb/manage/' + article.id}
                   title={article.title}
                   subtitle={article.excerpt}
                   meta={<span>{article.categoryNames?.join(', ')}</span>}
                   right={<span>{formatDateTime(article.updatedAt)}</span>} />
            ))}

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
