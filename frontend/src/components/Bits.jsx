import { Link } from 'react-router-dom'

/**
 * The muted-vs-teal split every status-ish word in the app uses: a live/
 * positive state gets the teal .pill, a closed/inactive one gets the grey
 * .pill--muted. One list here rather than scattering the same ternary across
 * every screen that shows a TicketStatus, ArticleStatus or active flag.
 */
const MUTED_VALUES = new Set([
  'WITHDRAWN', 'RESOLVED', 'CLOSED', 'ARCHIVED', 'INACTIVE', 'EXPIRED'
])

/** "IN_PROGRESS" -> "In progress" */
function humanize(value) {
  if (!value && value !== false) return ''
  const text = String(value)
  return text.charAt(0) + text.slice(1).toLowerCase().replace(/_/g, ' ')
}

/* ==========================================================================
   The small pieces every page shares. Each one renders exactly the markup the
   stylesheet already expects, so moving to React changed no class names and
   no layout.
   ========================================================================== */

/**
 * A message block with a coloured bar down its left edge.
 * kind: 'info' (default) | 'error' | 'warn'
 */
export function Notice({ kind = 'info', children, style }) {
  if (!children) return null
  const className = 'notice' + (kind === 'info' ? '' : ' notice--' + kind)
  return <div className={className} style={style}>{children}</div>
}

/**
 * A labelled input with room for a server-side error underneath.
 * Anything extra (a strength meter, a hint) goes in as children.
 */
export function Field({ id, label, error, hint, children, ...inputProps }) {
  return (
    <div className={'field' + (error ? ' is-invalid' : '')}>
      <label htmlFor={id}>{label}</label>
      <input id={id} name={id} {...inputProps} />
      {children}
      {hint && !error && <p className="hint">{hint}</p>}
      <p className="field-error">{error || ''}</p>
    </div>
  )
}

/** The same, for a <select>. */
export function SelectField({ id, label, error, options, ...selectProps }) {
  return (
    <div className={'field' + (error ? ' is-invalid' : '')}>
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

/**
 * The solid teal panel down the left of the auth pages. `points` may be plain
 * strings, or { lead, body } for the two-tier version used on the register page.
 */
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

      <p className="brand-panel__foot">SE2030 · Web-Based Help Desk System</p>
    </aside>
  )
}

/** The circle that shows a picture if there is one and initials if there is not. */
export function Avatar({ marks, src, small = false }) {
  const className = 'avatar' + (small ? ' avatar--sm' : '')
  return (
    <span className={className}>
      {src ? <img src={src} alt="" /> : marks}
    </span>
  )
}

/** A status word (TicketStatus, ArticleStatus, "Active"/"Suspended", ...) in a .pill. */
export function StatusPill({ value, label }) {
  if (value === undefined || value === null || value === '') return null
  const muted = MUTED_VALUES.has(String(value))
  return <span className={'pill' + (muted ? ' pill--muted' : '')}>{label || humanize(value)}</span>
}

/**
 * One row in a list of records — tickets, queue items, articles, users. This
 * is the row-list pattern every "many records" screen in the app shares: the
 * same .pref hairline-and-spacing rhythm the preference toggles already use,
 * not a bordered table and not a card grid.
 *
 * `to` makes the whole row a Link (for "open this record"); omit it and pass
 * `onClick` for a row that performs an action instead of navigating.
 */
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
 * A 1-5 star rating, used by the ticket feedback form (F3, US-12).
 *
 * WHY BUTTONS RATHER THAN A ROW OF DECORATIVE SPANS
 * -------------------------------------------------
 * A rating is an input, so it has to behave like one: reachable by Tab,
 * settable with Enter or Space, and announced to a screen reader as "Rate 4
 * out of 5". A div with an onClick is none of those things, and NFR 5.4 asks
 * for keyboard accessibility explicitly. Native <button> elements give all of
 * it for free rather than needing role/tabIndex/onKeyDown written by hand.
 *
 * `readOnly` renders the same markup with the buttons disabled, so the saved
 * rating looks identical to the one being chosen instead of being a second,
 * slightly different star row somewhere else in the file.
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
          {/* A filled or hollow star. aria-hidden because the button's own
              aria-label already says what this control does - without it a
              screen reader would read the character as well and announce the
              control twice. */}
          <span aria-hidden="true">{star <= value ? '★' : '☆'}</span>
        </button>
      ))}
    </div>
  )
}
