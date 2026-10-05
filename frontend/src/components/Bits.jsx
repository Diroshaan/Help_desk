import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { motion, useReducedMotion } from 'framer-motion'
import { formatDateTime } from '../api.js'

/**
 * Scrolls to an error (and focuses its input) so it isn't missed on a long form.
 * Only the first error on the page does this, so we scroll once.
 */
function useRevealError(active, focusInput, key) {
  const ref = useRef(null)
  useEffect(() => {
    if (!active) return
    const frame = requestAnimationFrame(() => {
      const el = ref.current
      if (!el) return
      const first = document.querySelector('.notice--error, .field.is-invalid')
      if (first !== el) return
      const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
      el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'center' })
      if (focusInput) el.querySelector('input, select, textarea')?.focus({ preventScroll: true })
    })
    return () => cancelAnimationFrame(frame)
  }, [active, focusInput, key])
  return ref
}

/* statuses shown with the grey pill instead of the teal one */
const MUTED_VALUES = new Set([
  'WITHDRAWN', 'RESOLVED', 'CLOSED', 'ARCHIVED', 'INACTIVE', 'EXPIRED'
])

/** "IN_PROGRESS" -> "In progress" */
export function humanize(value) {
  if (!value && value !== false) return ''
  const text = String(value)
  return text.charAt(0) + text.slice(1).toLowerCase().replace(/_/g, ' ')
}

/** Message block. kind: 'info' (default), 'error' or 'warn'. */
export function Notice({ kind = 'info', children, style }) {
  const ref = useRevealError(kind === 'error' && Boolean(children), false, typeof children === 'string' ? children : '')
  if (!children) return null
  const className = 'notice' + (kind === 'info' ? '' : ' notice--' + kind)
  /* so screen readers announce it: errors interrupt, other messages wait */
  const role = kind === 'error' ? 'alert' : 'status'
  return <div ref={ref} className={className} style={style} role={role}>{children}</div>
}

/**
 * Up to `max` contact numbers (the server also allows three). The first one is
 * the primary number and can't be removed, only cleared. Blank boxes are
 * dropped by the server.
 */
export function PhoneList({ values, onChange, max = 3, error }) {
  const list = values.length ? values : ['']
  const ref = useRevealError(Boolean(error), true, error)

  function update(index, value) {
    const next = [...list]
    next[index] = value
    onChange(next)
  }

  return (
    <div ref={ref} className={'field' + (error ? ' is-invalid' : '')}>
      <label htmlFor="phone-0">Contact number{max > 1 ? 's' : ''}</label>
      {list.map((value, index) => (
        <div className="row-between" key={index} style={{ gap: 10, marginTop: index ? 8 : 0 }}>
          <input id={'phone-' + index} name={'phone-' + index} type="tel" autoComplete="tel"
                 aria-label={index === 0 ? 'Primary contact number' : 'Contact number ' + (index + 1)}
                 value={value} onChange={e => update(index, e.target.value)} style={{ flex: 1 }} />
          {index > 0 && (
            <button type="button" className="btn btn--ghost"
                    onClick={() => onChange(list.filter((_, i) => i !== index))}>
              Remove
            </button>
          )}
        </div>
      ))}
      {list.length < max && (
        <button type="button" className="text-link" style={{ marginTop: 8, background: 'none', border: 0, padding: 0, cursor: 'pointer', justifySelf: 'start', textAlign: 'left', width: 'fit-content' }}
                onClick={() => onChange([...list, ''])}>
          + Add another number
        </button>
      )}
      <p className="hint">The first number is the one the help desk calls first. Up to {max}.</p>
      <p className="field-error">{error || ''}</p>
    </div>
  )
}

/** Labelled input with its error underneath. Extras (e.g. strength meter) go in children. */
export function Field({ id, label, error, hint, children, ...inputProps }) {
  const ref = useRevealError(Boolean(error), true, error)
  return (
    <div ref={ref} className={'field' + (error ? ' is-invalid' : '')}>
      <label htmlFor={id}>{label}</label>
      <input id={id} name={id} {...inputProps} />
      {children}
      {hint && !error && <p className="hint">{hint}</p>}
      <p className="field-error">{error || ''}</p>
    </div>
  )
}

export function TextAreaField({ id, label, error, hint, ...areaProps }) {
  const ref = useRevealError(Boolean(error), true, error)
  return (
    <div ref={ref} className={'field' + (error ? ' is-invalid' : '')}>
      <label htmlFor={id}>{label}</label>
      <textarea id={id} name={id} {...areaProps} />
      {hint && !error && <p className="hint">{hint}</p>}
      <p className="field-error">{error || ''}</p>
    </div>
  )
}

export function SelectField({ id, label, error, options, ...selectProps }) {
  const ref = useRevealError(Boolean(error), true, error)
  return (
    <div ref={ref} className={'field' + (error ? ' is-invalid' : '')}>
      <label htmlFor={id}>{label}</label>
      <select id={id} name={id} {...selectProps}>
        {options.map(option => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
      <p className="field-error">{error || ''}</p>
    </div>
  )
}

/** Teal panel on the auth pages. points are strings or { lead, body }. */
export function BrandPanel({ home = '/', heading, points }) {
  return (
    <aside className="brand-panel">
      <Link className="brand" to={home}>UNIHELP</Link>

      <div className="brand-panel__body rise rise-1">
        <h1>{heading}</h1>
        <ul className="brand-points">
          {points.map((point, index) => (
            <li key={index}>
              {typeof point === 'string' ? point : (
                <>
                  <strong>{point.lead}</strong>
                  {point.body}
                </>
              )}
            </li>
          ))}
        </ul>
      </div>

    </aside>
  )
}

export function Avatar({ marks, src, small = false }) {
  const className = 'avatar' + (small ? ' avatar--sm' : '')
  return (
    <span className={className}>
      {src ? <img src={src} alt="" /> : marks}
    </span>
  )
}

export function StatusPill({ value, label }) {
  if (value === undefined || value === null || value === '') return null
  const muted = MUTED_VALUES.has(String(value))
  return <span className={'pill' + (muted ? ' pill--muted' : '')}>{label || humanize(value)}</span>
}

/** One row in a record list. Pass `to` to make it a link, or `onClick` for an action. */
export function Row({ to, title, subtitle, meta, right, onClick }) {
  const inner = (
    <>
      <div className="pref__text">
        <strong>{title}</strong>
        {subtitle && <span>{subtitle}</span>}
      </div>
      <div className="row-side">
        {meta}
        {right}
      </div>
    </>
  )

  if (to) {
    return <Link className="pref row-link" to={to}>{inner}</Link>
  }
  return (
    <div className={'pref' + (onClick ? ' row-link' : '')} onClick={onClick} role={onClick ? 'button' : undefined}>
      {inner}
    </div>
  )
}

/**
 * 1-5 star rating for ticket feedback. Real buttons so it works with the
 * keyboard and screen readers. readOnly shows a saved rating.
 */
export function Rating({ value, onChange, readOnly = false }) {
  const stars = [1, 2, 3, 4, 5]
  return (
    <div className="stars" role={readOnly ? 'img' : 'group'}
         aria-label={readOnly ? value + ' out of 5' : 'Rating, 1 to 5'}>
      {stars.map(star => (
        <button
          key={star}
          type="button"
          className={'stars__s' + (star <= value ? ' is-on' : '')}
          disabled={readOnly}
          aria-label={'Rate ' + star + ' out of 5'}
          aria-pressed={!readOnly && star === value}
          onClick={readOnly ? undefined : () => onChange(star)}
        >
          <span aria-hidden="true">{star <= value ? '★' : '☆'}</span>
        </button>
      ))}
    </div>
  )
}

/**
 * Ticket status history, oldest first, shared by the student and officer
 * pages. Steps fade in one after another unless reduced motion is on.
 */
export function StatusTimeline({ entries, emptyText = 'No status changes recorded yet.' }) {
  const reduce = useReducedMotion()
  if (!entries || entries.length === 0) return <p className="empty">{emptyText}</p>

  return (
    <ol className="status-timeline">
      {entries.map((entry, index) => (
        <motion.li key={entry.sequenceNo}
                   className={'status-timeline__step' + (index === entries.length - 1 ? ' is-current' : '')}
                   initial={reduce ? false : { opacity: 0, x: -6 }}
                   animate={{ opacity: 1, x: 0 }}
                   transition={{ duration: 0.28, delay: reduce ? 0 : index * 0.08, ease: [0.22, 0.61, 0.36, 1] }}>
          <span className="status-timeline__dot" aria-hidden="true" />
          <div className="status-timeline__body">
            <strong>
              {entry.fromStatus
                ? humanize(entry.fromStatus) + ' → ' + humanize(entry.toStatus)
                : 'Submitted as ' + humanize(entry.toStatus)}
            </strong>
            {/* no recorded officer: show it as "Help desk" */}
            <span>{!entry.changedBy || entry.changedBy === 'Unknown' ? 'Help desk' : entry.changedBy} · {formatDateTime(entry.changedAt)}</span>
          </div>
        </motion.li>
      ))}
    </ol>
  )
}

/**
 * Two-step button for actions that can't be undone. Inline instead of
 * window.confirm(), which some browsers block.
 */
export function ConfirmButton({ label, question, confirmLabel, onConfirm, busy = false, kind = 'danger', disabled = false }) {
  const [asking, setAsking] = useState(false)
  const yesRef = useRef(null)
  useEffect(() => { if (asking) yesRef.current?.focus() }, [asking])

  if (!asking) {
    return (
      // outline first; only the confirm step is solid red
      <button type="button" className={'btn btn--' + (kind === 'danger' ? 'danger-line' : kind)} disabled={disabled || busy}
              onClick={() => setAsking(true)}>
        {busy ? 'Working…' : label}
      </button>
    )
  }

  return (
    <span className="confirm" role="group" aria-label={question}>
      <span className="confirm__q">{question}</span>
      <button type="button" ref={yesRef} className={'btn btn--' + kind}
              onClick={() => { setAsking(false); onConfirm() }}>
        {confirmLabel || label}
      </button>
      <button type="button" className="btn btn--ghost" onClick={() => setAsking(false)}>Cancel</button>
    </span>
  )
}

/** Tabs with a sliding underline (e.g. Active / Archived). */
export function Tabs({ tabs, value, onChange, label }) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {tabs.map(tab => (
        <button key={tab.value} type="button" role="tab"
                aria-selected={value === tab.value}
                className={'tabs__tab' + (value === tab.value ? ' is-active' : '')}
                onClick={() => onChange(tab.value)}>
          {tab.label}
          {tab.count !== undefined && tab.count !== null && <span className="tabs__count">{tab.count}</span>}
          {value === tab.value && <motion.span layoutId={'tab-ink-' + (label || 'tabs')} className="tabs__ink" />}
        </button>
      ))}
    </div>
  )
}

/** One headline number on a dashboard. The number counts up once on load. */
export function StatTile({ label, value, suffix, note }) {
  const shown = useCountUp(typeof value === 'number' ? value : null)
  return (
    <div className="stat">
      <span className="stat__label">{label}</span>
      <span className="stat__value">
        {value === null || value === undefined ? '—' : (typeof value === 'number' ? shown : value)}
        {suffix && value !== null && value !== undefined && <small>{suffix}</small>}
      </span>
      {note && <span className="stat__note">{note}</span>}
    </div>
  )
}

function useCountUp(target) {
  const reduce = useReducedMotion()
  const [shown, setShown] = useState(target ?? 0)
  useEffect(() => {
    if (target === null) return
    if (reduce) { setShown(target); return }
    const decimals = Number.isInteger(target) ? 0 : 1
    const start = performance.now()
    let frame
    const tick = now => {
      const p = Math.min(1, (now - start) / 700)
      const eased = 1 - Math.pow(1 - p, 3)
      setShown(Number((target * eased).toFixed(decimals)))
      if (p < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, reduce])
  return shown
}

/** Horizontal bar chart for a label/count breakdown. */
export function BarList({ items, empty = 'Nothing to show yet.' }) {
  const reduce = useReducedMotion()
  if (!items || items.length === 0) return <p className="empty">{empty}</p>
  const peak = Math.max(1, ...items.map(i => i.value))
  return (
    <div className="bars">
      {items.map((item, index) => (
        <div className="bar-row bar-row--wide" key={item.key}>
          <span className="bar-row__label">{item.label}</span>
          <div className="bar-row__track" title={item.label + ': ' + item.value}>
            <motion.div className="bar-row__fill"
                        initial={reduce ? false : { width: 0 }}
                        animate={{ width: (item.value / peak) * 100 + '%' }}
                        transition={{ duration: 0.7, delay: reduce ? 0 : index * 0.06, ease: [0.22, 0.61, 0.36, 1] }} />
          </div>
          <span className="bar-row__value mono">{item.value}</span>
        </div>
      ))}
    </div>
  )
}
