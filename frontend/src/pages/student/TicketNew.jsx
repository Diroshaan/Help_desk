import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { API, errorMessage, fieldErrors, request } from '../../api.js'
import { Field, Notice, SelectField } from '../../components/Bits.jsx'
import { Sidebar } from '../../components/Sidebar.jsx'

const PRIORITIES = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
  { value: 'URGENT', label: 'Urgent' }
]

export default function TicketNew() {
  const navigate = useNavigate()
  const [categories, setCategories] = useState([])
  const [form, setForm] = useState({ subject: '', description: '', category: '', priority: 'MEDIUM' })
  const [errors, setErrors] = useState({})
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    request(API.ticketCategories).then(r => {
      if (r.ok) {
        setCategories(r.data || [])
        if (r.data && r.data.length) setForm(f => ({ ...f, category: f.category || r.data[0] }))
      }
    })
  }, [])

  function set(name) {
    return event => setForm(current => ({ ...current, [name]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setErrors({}); setNotice(null); setBusy(true)

    try {
      const result = await request(API.tickets, { method: 'POST', body: form })

      if (result.ok) {
        navigate('/tickets/' + result.data.id)
        return
      }

      const fields = fieldErrors(result, ['subject', 'description', 'category', 'priority'])
      if (Object.keys(fields).length) setErrors(fields)
      else setNotice({ kind: 'error', text: errorMessage(result, 'We could not submit that ticket.') })
    } catch {
      setNotice({ kind: 'error', text: 'Could not reach the server. The ticket was not submitted.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="shell">
      <Sidebar />
      <main className="content">
        <div className="content-col rise rise-1">
          <div className="page-head"><h1>Submit a ticket</h1></div>

          {notice && <Notice kind={notice.kind} style={{ margin: '18px 0 0' }}>{notice.text}</Notice>}

          <section className="section">
            <form className="form" onSubmit={handleSubmit} noValidate>
              <Field id="subject" label="Subject" type="text"
                     value={form.subject} onChange={set('subject')} error={errors.subject} />

              <div className="field-row">
                <SelectField id="category" label="Category"
                             options={categories.map(c => ({ value: c, label: c }))}
                             value={form.category} onChange={set('category')} error={errors.category} />
                <SelectField id="priority" label="Priority" options={PRIORITIES}
                             value={form.priority} onChange={set('priority')} error={errors.priority} />
              </div>

              <div className="field">
                <label htmlFor="description">Description</label>
                <textarea id="description" name="description" rows={6}
                          value={form.description} onChange={set('description')} />
                <p className="field-error">{errors.description || ''}</p>
              </div>

              <div className="btn-row">
                <button type="submit" className="btn btn--primary" disabled={busy}>
                  {busy ? 'Submitting…' : 'Submit ticket'}
                </button>
              </div>
            </form>
          </section>
        </div>
      </main>
    </div>
  )
}
