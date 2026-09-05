import { Link } from 'react-router-dom'

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
