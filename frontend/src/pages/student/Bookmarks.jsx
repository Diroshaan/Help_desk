import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { API, errorMessage, request } from '../../api.js'
import { Field, Notice, StatusPill } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

export default function Bookmarks() {
  const [folders, setFolders] = useState([])
  const [bookmarks, setBookmarks] = useState([])
  const [tickets, setTickets] = useState({})     // id -> TicketResponse
  const [activeFolder, setActiveFolder] = useState('all')   // 'all' | 'none' | folderId
  const [newFolderName, setNewFolderName] = useState('')
  const [renaming, setRenaming] = useState(null)   // folder id being renamed
  const [renameValue, setRenameValue] = useState('')
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(null)
  const [loading, setLoading] = useState(true)

  async function load() {
    setLoading(true)
    const [folderResult, bookmarkResult, ticketResult] = await Promise.all([
      request(API.bookmarkFolders),
      request(API.bookmarks),
      request(API.tickets)
    ])

    if (folderResult.ok) setFolders(folderResult.data)
    if (bookmarkResult.ok) setBookmarks(bookmarkResult.data)
    if (ticketResult.ok) {
      const map = {}
      ticketResult.data.forEach(t => { map[t.id] = t })
      setTickets(map)
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  async function createFolder(event) {
    event.preventDefault()
    if (!newFolderName.trim()) return
    setBusy('create-folder')
    const result = await request(API.bookmarkFolders, { method: 'POST', body: { name: newFolderName.trim() } })
    if (result.ok) {
      setFolders(current => [...current, result.data])
      setNewFolderName('')
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not create that folder.') })
    }
    setBusy(null)
  }

  async function saveRename(folderId) {
    if (!renameValue.trim()) return
    setBusy('rename-' + folderId)
    const result = await request(API.bookmarkFolderItem(folderId), { method: 'PATCH', body: { name: renameValue.trim() } })
    if (result.ok) {
      setFolders(current => current.map(f => f.id === folderId ? result.data : f))
      setRenaming(null)
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not rename that folder.') })
    }
    setBusy(null)
  }

  async function deleteFolder(folderId) {
    setBusy('delete-folder-' + folderId)
    const result = await request(API.bookmarkFolderItem(folderId), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setFolders(current => current.filter(f => f.id !== folderId))
      setBookmarks(current => current.map(b => b.folderId === folderId ? { ...b, folderId: null } : b))
      if (activeFolder === folderId) setActiveFolder('all')
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not delete that folder.') })
    }
    setBusy(null)
  }

  async function refreshFolderCounts() {
    const result = await request(API.bookmarkFolders)
    if (result.ok) setFolders(result.data)
  }

  async function moveBookmark(bookmarkId, folderId) {
    setBusy('move-' + bookmarkId)
    const result = await request(API.bookmarkFolder(bookmarkId), { method: 'PATCH', body: { folderId } })
    if (result.ok) {
      setBookmarks(current => current.map(b => b.id === bookmarkId ? result.data : b))
      // bookmarkCount lives on the folder, not the bookmark, so the move
      // response alone can't keep both folders' counts in sync.
      await refreshFolderCounts()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not move that bookmark.') })
    }
    setBusy(null)
  }

  async function removeBookmark(bookmarkId) {
    setBusy('remove-' + bookmarkId)
    const result = await request(API.bookmark(bookmarkId), { method: 'DELETE' })
    if (result.ok || result.status === 204) {
      setBookmarks(current => current.filter(b => b.id !== bookmarkId))
      await refreshFolderCounts()
    } else {
      setNotice({ kind: 'error', text: errorMessage(result, 'We could not remove that bookmark.') })
    }
    setBusy(null)
  }

  const visible = bookmarks.filter(b => {
    if (activeFolder === 'all') return true
    if (activeFolder === 'none') return !b.folderId
    return b.folderId === activeFolder
  })

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Bookmarks</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <h2>Folders</h2>

            {folders.length === 0 && <p className="empty">You have not created any folders yet.</p>}

            {folders.map(folder => (
              <div className="pref" key={folder.id}>
                {renaming === folder.id ? (
                  <div className="pref__text" style={{ flex: 1, maxWidth: 320 }}>
                    <Field id={'rename-' + folder.id} label="Folder name" type="text"
                           value={renameValue} onChange={e => setRenameValue(e.target.value)} />
                  </div>
                ) : (
                  <div className="pref__text">
                    <strong>{folder.name}</strong>
                    <span>{folder.bookmarkCount} bookmark{folder.bookmarkCount === 1 ? '' : 's'}</span>
                  </div>
                )}

                <div className="row-side">
                  {renaming === folder.id ? (
                    <>
                      <button type="button" className="btn btn--primary" disabled={busy === 'rename-' + folder.id}
                              onClick={() => saveRename(folder.id)}>Save</button>
                      <button type="button" className="btn btn--ghost" onClick={() => setRenaming(null)}>Cancel</button>
                    </>
                  ) : (
                    <>
                      <button type="button" className="text-link" style={{ background: 'none', border: 0, cursor: 'pointer' }}
                              onClick={() => { setRenaming(folder.id); setRenameValue(folder.name) }}>Rename</button>
                      <button type="button" className="danger-link" style={{ background: 'none', border: 0, cursor: 'pointer' }}
                              disabled={busy === 'delete-folder-' + folder.id}
                              onClick={() => deleteFolder(folder.id)}>Delete</button>
                    </>
                  )}
                </div>
              </div>
            ))}

            <form className="btn-row" style={{ marginTop: 18 }} onSubmit={createFolder}>
              <input type="text" placeholder="New folder name" value={newFolderName}
                     onChange={e => setNewFolderName(e.target.value)} style={{ maxWidth: 240 }} />
              <button type="submit" className="btn btn--ghost" disabled={busy === 'create-folder'}>Add folder</button>
            </form>
          </section>

          <section className="section">
            <h2>Bookmarked tickets</h2>

            <div className="chips" style={{ marginTop: 0, marginBottom: 20 }}>
              <button type="button" className="chip" aria-current={activeFolder === 'all'} onClick={() => setActiveFolder('all')}>All</button>
              <button type="button" className="chip" aria-current={activeFolder === 'none'} onClick={() => setActiveFolder('none')}>Unfiled</button>
              {folders.map(f => (
                <button type="button" key={f.id} className="chip" aria-current={activeFolder === f.id}
                        onClick={() => setActiveFolder(f.id)}>{f.name}</button>
              ))}
            </div>

            {loading && <p className="empty">Loading bookmarks…</p>}

            {!loading && visible.length === 0 && <p className="empty">Nothing bookmarked here yet.</p>}

            {!loading && visible.map(bookmark => {
              const ticket = tickets[bookmark.ticketId]
              return (
                <div className="pref" key={bookmark.id}>
                  <div className="pref__text">
                    <strong>{ticket ? ticket.subject : 'Ticket #' + bookmark.ticketId}</strong>
                    <span>{ticket ? ticket.category : ''}</span>
                  </div>
                  <div className="row-side">
                    {ticket && <StatusPill value={ticket.status} />}
                    <select value={bookmark.folderId || ''} disabled={busy === 'move-' + bookmark.id}
                            onChange={e => moveBookmark(bookmark.id, e.target.value ? Number(e.target.value) : null)}>
                      <option value="">No folder</option>
                      {folders.map(f => <option key={f.id} value={f.id}>{f.name}</option>)}
                    </select>
                    {ticket && <Link className="text-link" to={'/tickets/' + ticket.id}>Open</Link>}
                    <button type="button" className="btn btn--ghost" disabled={busy === 'remove-' + bookmark.id}
                            onClick={() => removeBookmark(bookmark.id)}>Remove</button>
                  </div>
                </div>
              )
            })}
          </section>
        </div>
      </main>
    </div>
  )
}
