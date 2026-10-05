import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, formatDateTime, request } from '../../api.js'
import { Row } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

/**
 * Articles this student has saved. Kept apart from ticket bookmarks (/bookmarks)
 * because they are different records and only tickets have folders. Read-only:
 * an article is unsaved from its own page.
 */
export default function SavedArticles() {
  const [articles, setArticles] = useState([])
  const [loading, setLoading] = useState(true)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    request(API.articlesBookmarked).then(r => {
      if (r.ok) setArticles(r.data || [])
      else setFailed(true)
      setLoading(false)
    })
  }, [])

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Saved articles</h1></div>

          <p className="section-note">
            Articles you have saved from the knowledge base. Open one to read it
            or to remove it from this list.
          </p>

          <section className="section">
            {loading && <p className="empty">Loading your saved articles…</p>}

            {!loading && failed && (
              <p className="empty">We could not load your saved articles just now.</p>
            )}

            {!loading && !failed && articles.length === 0 && (
              <>
                <p className="empty">You have not saved any articles yet.</p>
                <div className="btn-row" style={{ marginTop: 16 }}>
                  <Link className="btn btn--ghost" to="/kb">Browse the knowledge base</Link>
                </div>
              </>
            )}

            {!loading && articles.map(article => (
              <Row
                key={article.id}
                to={'/kb/' + article.id}
                title={article.title}
                subtitle={article.excerpt}
                meta={
                  <span className="foot-note">
                    {(article.categoryNames || []).join(', ')}
                    {article.categoryNames?.length ? ' · ' : ''}
                    Updated {formatDateTime(article.updatedAt)}
                  </span>
                }
              />
            ))}
          </section>
        </div>
      </main>
    </div>
  )
}
