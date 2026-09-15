import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, formatDateTime, request } from '../../api.js'
import { Row } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

/**
 * F5 - the articles this student has saved.
 *
 * WHY THIS IS A SEPARATE SCREEN FROM /bookmarks
 * ---------------------------------------------
 * The two look similar and are deliberately not merged. A ticket bookmark
 * (F3) and an article bookmark (F5) are different records in different
 * tables, owned by different features, with different behaviour: ticket
 * bookmarks can be filed into folders and article bookmarks cannot. Putting
 * them on one screen would mean one list where half the rows have a folder
 * dropdown and half do not, and a "move to folder" action that silently does
 * nothing for half of them.
 *
 * The backend draws the same line - GET /api/bookmarks and
 * GET /api/articles/bookmarked are separate endpoints returning different
 * shapes - and the UI follows it rather than papering over it.
 *
 * Read-only on purpose: an article is unsaved from the article itself, where
 * the student can see what they are removing. A "remove" button next to a
 * title alone invites removing the wrong one.
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
