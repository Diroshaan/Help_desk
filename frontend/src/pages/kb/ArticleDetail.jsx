import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { API, errorMessage, formatDateTime, request } from '../../api.js'
import { Notice, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'
import { useSession } from '../../hooks/useSession.jsx'

export default function ArticleDetail() {
  const { id } = useParams()
  const { role } = useSession()
  const [article, setArticle] = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)

  // F5 - a student's saved articles. `saved` is derived by asking for the
  // student's bookmarked list and checking whether this article is in it,
  // rather than by a per-article "is it saved" endpoint, because
  // GET /api/articles/bookmarked is the only read the backend offers. One
  // request answers the question for this page and costs nothing extra.
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    setLoading(true); setNotFound(false)
    request(API.article(id)).then(r => {
      if (r.ok) setArticle(r.data)
      else setNotFound(true)
      setLoading(false)
    })
  }, [id])

  // Only a student can bookmark an article - the backend resolves the owner
  // from the session through StudentRepository, so an officer calling it gets
  // "No student account for ...". Asking at all would be a guaranteed error,
  // so the request is not made and the button is not rendered.
  useEffect(() => {
    if (role !== 'STUDENT') return
    request(API.articlesBookmarked).then(r => {
      if (r.ok) setSaved((r.data || []).some(a => a.id === Number(id)))
    })
  }, [id, role])

  /**
   * Save or unsave this article (F5).
   *
   * The state is flipped only after the server confirms, never optimistically.
   * An optimistic flip here would be a lie in the one case that matters: the
   * unique constraint uq_article_bookmark_student_article rejects a duplicate
   * save, and the button would already be showing "Saved" while nothing had
   * been stored.
   */
  async function toggleSaved() {
    setBusy(true); setNotice(null)
    try {
      const result = await request(API.articleBookmark(id), { method: saved ? 'DELETE' : 'POST' })
      if (result.ok || result.status === 204) {
        setSaved(!saved)
      } else {
        setNotice({ kind: 'error', text: errorMessage(result, 'We could not update your saved articles.') })
      }
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(false)
    }
  }

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

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <div className="btn-row" style={{ marginTop: 12 }}>
            {role === 'STUDENT' && (
              <button type="button" className="btn btn--ghost" onClick={toggleSaved} disabled={busy}>
                {saved ? 'Remove from saved' : 'Save this article'}
              </button>
            )}
            {role === 'OFFICER' && (
              <Link className="btn btn--ghost" to={'/kb/manage/' + article.id}>Edit this article</Link>
            )}
          </div>

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
