import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { API, errorMessage, fieldErrors, request } from '../../api.js'
import { Field, Notice, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

export default function ArticleEditor() {
  const { id } = useParams()          // absent on /kb/manage/new
  const navigate = useNavigate()
  const isNew = !id

  const [allCategories, setAllCategories] = useState([])
  const [article, setArticle] = useState(null)     // ArticleDetailResponse, once it exists
  const [form, setForm] = useState({ title: '', body: '', tags: '', categoryIds: [] })
  const [relatedId, setRelatedId] = useState('')
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(!isNew)

  useEffect(() => {
    request(API.categories).then(r => { if (r.ok) setAllCategories(r.data || []) })
  }, [])

  useEffect(() => {
    if (isNew) return
    setLoading(true)
    request(API.article(id)).then(r => {
      if (r.ok) {
        setArticle(r.data)
        setForm({
          title: r.data.title,
          body: r.data.body,
          tags: (r.data.tags || []).join(', '),
          // ArticleDetailResponse only carries category NAMES, not ids — the
          // ids are recovered by matching against the full category list,
          // which is the only place both id and name exist together.
          categoryIds: allCategories.filter(c => (r.data.categoryNames || []).includes(c.name)).map(c => c.id)
        })
      } else {
        setNotice({ kind: 'error', text: 'We could not load that article.' })
      }
      setLoading(false)
    })
    // Deliberately re-runs once allCategories arrives too, so the category
    // checkboxes still get preselected even if that request was still in
    // flight when the article itself came back.
  }, [id, isNew, allCategories.length])

  function toggleCategory(categoryId) {
    setForm(current => ({
      ...current,
      categoryIds: current.categoryIds.includes(categoryId)
        ? current.categoryIds.filter(c => c !== categoryId)
        : [...current.categoryIds, categoryId]
    }))
  }

  async function save(event) {
    event.preventDefault()
    setErrors({}); setNotice(null); setBusy('save')

    const payload = {
      title: form.title.trim(),
      body: form.body,
      tags: form.tags.split(',').map(t => t.trim()).filter(Boolean),
      categoryIds: form.categoryIds
    }

    try {
      const result = isNew
        ? await request(API.articles, { method: 'POST', body: payload })
        : await request(API.article(id), { method: 'PUT', body: payload })

      if (result.ok) {
        setArticle(result.data)
        setNotice({ kind: 'info', text: 'Saved.' })
        if (isNew) navigate('/kb/manage/' + result.data.id, { replace: true })
        return
      }

      const fields = fieldErrors(result, ['title', 'body'])
      if (Object.keys(fields).length) setErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not save this article.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server.' })
    } finally {
      setBusy(null)
    }
  }

  async function publish() {
    setBusy('publish'); setNotice(null)
    const result = await request(API.articlePublish(id), { method: 'POST' })
    if (result.ok) { setArticle(result.data); setNotice({ kind: 'info', text: 'Published.' }) }
    else setNotice({ kind: 'error', text: errorMessage(result, 'We could not publish this article.') })
    setBusy(null)
  }

  async function archive() {
    setBusy('archive'); setNotice(null)
    const result = await request(API.articleArchive(id), { method: 'POST' })
    if (result.ok) { setArticle(result.data); setNotice({ kind: 'info', text: 'Archived.' }) }
    else setNotice({ kind: 'error', text: errorMessage(result, 'We could not archive this article.') })
    setBusy(null)
  }

  async function linkRelated(event) {
    event.preventDefault()
    if (!relatedId.trim()) return
    setBusy('related'); setNotice(null)
    const result = await request(API.articleRelated(id), { method: 'POST', body: { relatedArticleId: Number(relatedId) } })
    if (result.ok) { setArticle(result.data); setRelatedId('') }
    else setNotice({ kind: 'error', text: errorMessage(result, 'We could not link that article.') })
    setBusy(null)
  }

  async function unlinkRelated(relatedArticleId) {
    setBusy('unlink-' + relatedArticleId)
    const result = await request(API.articleRelatedItem(id, relatedArticleId), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setArticle(current => ({
        ...current,
        relatedArticles: current.relatedArticles.filter(r => r.id !== relatedArticleId)
      }))
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not unlink that article.') })
    }
    setBusy(null)
  }

  if (loading) {
    return <div className="shell"><Sidebar /><main className="content">
      <div className="content-col"><p className="empty">Loading…</p></div>
    </main></div>
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <Link className="back-link" to="/kb/manage">&larr; Back to manage articles</Link>

          <div className="page-head" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
            <h1>{isNew ? 'New article' : 'Edit article'}</h1>
            {article && <StatusPill value={article.status} />}
          </div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <form className="form" style={{ marginTop: 0 }} onSubmit={save} noValidate>
              <Field id="title" label="Title" type="text"
                     value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                     error={errors.title} />

              <div className="field">
                <label htmlFor="body">Body</label>
                <textarea id="body" rows={10} value={form.body}
                          onChange={e => setForm(f => ({ ...f, body: e.target.value }))} />
                <p className="field-error">{errors.body || ''}</p>
              </div>

              <Field id="tags" label="Tags" type="text" hint="Comma-separated, e.g. wifi, printing, vpn"
                     value={form.tags} onChange={e => setForm(f => ({ ...f, tags: e.target.value }))} />

              <div className="field">
                <label>Categories</label>
                <div className="chips">
                  {allCategories.map(c => (
                    <button type="button" key={c.id} className="chip"
                            aria-current={form.categoryIds.includes(c.id)}
                            onClick={() => toggleCategory(c.id)}>
                      {c.name} — {c.departmentName}
                    </button>
                  ))}
                </div>
              </div>

              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy === 'save'}>
                  {busy === 'save' ? 'Saving…' : 'Save'}
                </button>
                {article?.status === 'DRAFT' && (
                  <button type="button" className="btn btn--ghost" disabled={busy === 'publish'} onClick={publish}>
                    Publish
                  </button>
                )}
                {article?.status === 'PUBLISHED' && (
                  <button type="button" className="btn btn--ghost" disabled={busy === 'archive'} onClick={archive}>
                    Archive
                  </button>
                )}
              </div>
            </form>
          </section>

          {article && (
            <section className="section">
              <h2>Related articles</h2>

              {(!article.relatedArticles || article.relatedArticles.length === 0) && (
                <p className="empty">No related articles linked yet.</p>
              )}

              {article.relatedArticles?.map(related => (
                <div className="pref" key={related.id}>
                  <div className="pref__text">
                    <strong>{related.title}</strong>
                    <span>{related.excerpt}</span>
                  </div>
                  <button type="button" className="btn btn--ghost" disabled={busy === 'unlink-' + related.id}
                          onClick={() => unlinkRelated(related.id)}>Unlink</button>
                </div>
              ))}

              <form className="btn-row" style={{ marginTop: 18 }} onSubmit={linkRelated}>
                <input type="number" placeholder="Article ID to link" value={relatedId}
                       onChange={e => setRelatedId(e.target.value)} style={{ maxWidth: 200 }} />
                <button type="submit" className="btn btn--ghost" disabled={busy === 'related'}>Link article</button>
              </form>
            </section>
          )}
        </div>
      </main>
    </div>
  )
}
