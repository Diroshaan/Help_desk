import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API, formatDateTime, request } from '../../api.js'
import { Notice, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'
import { useSession } from '../../hooks/useSession.jsx'

export default function ArticleDetail() {
  const { id } = useParams()
  const { role } = useSession()
  const [article, setArticle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  useEffect(() => {
    setLoading(true); setNotFound(false)
    request(API.article(id)).then(r => {
      if (r.ok) setArticle(r.data)
      else setNotFound(true)
      setLoading(false)
    })
  }, [id])

  if (loading) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col"><p className="empty">Loading article…</p></div>
    </main></div>
  }

  if (notFound) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col">
        <div className="page-head"><h1>Article not found</h1></div>
        <div className="btn-row" style={{ marginTop: 20 }}>
          <Link className="btn btn--ghost" to="/kb">Back to the knowledge base</Link>
        </div>
      </div>
    </main></div>
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <Link className="back-link" to="/kb">&larr; Back to the knowledge base</Link>

          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>{article.title}</h1>
            {role === 'OFFICER' && <StatusPill value={article.status} />}
          </div>

          <p className="section-note" style={{ marginTop: 8 }}>
            By {article.authorName} · Updated {formatDateTime(article.updatedAt)}
          </p>

          {role === 'OFFICER' && (
            <div className="btn-row" style={{ marginTop: 12 }}>
              <Link className="btn btn--ghost" to={'/kb/manage/' + article.id}>Edit this article</Link>
            </div>
          )}

          <section className="section">
            <p style={{ whiteSpace: 'pre-wrap' }}>{article.body}</p>
          </section>

          {article.tags?.length > 0 && (
            <section className="section">
              <h2>Tags</h2>
              <div className="chips">
                {article.tags.map(tag => <span className="chip" key={tag}>{tag}</span>)}
              </div>
            </section>
          )}

          {article.categoryNames?.length > 0 && (
            <section className="section">
              <h2>Categories</h2>
              <p>{article.categoryNames.join(', ')}</p>
            </section>
          )}

          <section className="section">
            <h2>Related articles</h2>
            {(!article.relatedArticles || article.relatedArticles.length === 0) ? (
              <p className="empty">No related articles linked yet.</p>
            ) : (
              article.relatedArticles.map(related => (
                <div className="pref" key={related.id}>
                  <div className="pref__text">
                    <strong>{related.title}</strong>
                    <span>{related.excerpt}</span>
                  </div>
                  <Link className="text-link" to={'/kb/' + related.id}>Read</Link>
                </div>
              ))
            )}
          </section>
        </div>
      </main>
    </div>
  )
}
