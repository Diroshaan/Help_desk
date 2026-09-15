import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, formatDateTime, request, withQuery } from '../../api.js'
import { Field, Notice, Row, SelectField } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'
import { useSession } from '../../hooks/useSession.jsx'

const PAGE_SIZE = 20

export default function Articles() {
  const { role } = useSession()
  const [categories, setCategories] = useState([])
  const [q, setQ] = useState('')
  const [category, setCategory] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    request(API.categories).then(r => { if (r.ok) setCategories(r.data || []) })
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true); setError('')

    request(withQuery(API.articles, { q: q || undefined, category: category || undefined, page, size: PAGE_SIZE })).then(r => {
      if (cancelled) return
      if (r.ok) setResult(r.data)
      else setError('We could not load the knowledge base.')
      setLoading(false)
    }).catch(() => { if (!cancelled) { setError('Could not reach the server.'); setLoading(false) } })

    return () => { cancelled = true }
  }, [q, category, page])

  const articles = result?.content || []

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>Knowledge base</h1>
            {role === 'OFFICER' && <Link className="btn btn--ghost" to="/kb/manage">Manage articles</Link>}
          </div>

          <section className="section">
            <div className="field-row">
              <Field id="q" label="Search" type="text" placeholder="Keyword"
                     value={q} onChange={e => { setPage(0); setQ(e.target.value) }} />
              <SelectField id="category" label="Category"
                           options={[{ value: '', label: 'Any category' }, ...categories.map(c => ({ value: c.id, label: c.name + ' — ' + c.departmentName }))]}
                           value={category} onChange={e => { setPage(0); setCategory(e.target.value) }} />
            </div>
          </section>

          <section className="section">
            {error && <Notice kind="error">{error}</Notice>}
            {!error && loading && <p className="empty">Loading articles…</p>}
            {!error && !loading && articles.length === 0 && <p className="empty">No articles match your search.</p>}

            {!error && !loading && articles.map(article => (
              <Row key={article.id} to={'/kb/' + article.id}
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
