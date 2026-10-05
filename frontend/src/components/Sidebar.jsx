import { Fragment } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../hooks/useSession.jsx'
import { useUnreadCount } from '../hooks/useUnreadCount.js'
import { ROUTES } from '../routes.jsx'

/**
 * Sidebar for signed-in pages. Links come from routes.jsx: any route with a
 * `nav` entry that the current role can access.
 */
export function Sidebar() {
  const { role, user, student, signOut } = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  // guests have no inbox
  const unread = useUnreadCount(Boolean(role))

  const sections = []
  ROUTES.forEach(route => {
    if (!route.nav || !Array.isArray(route.access) || !route.access.includes(role)) return
    let section = sections.find(s => s.label === route.nav.section)
    if (!section) {
      section = { label: route.nav.section, links: [] }
      sections.push(section)
    }
    section.links.push({ path: route.path, label: route.nav.label })
  })

  async function handleSignOut() {
    // Navigate away before signing out, otherwise this page's guard sees a
    // guest and redirects to the login form.
    navigate('/')
    await signOut()
  }

  const displayName = student?.fullName || user?.displayName || ''
  const secondary = student?.studentId || (role ? role.charAt(0) + role.slice(1).toLowerCase() : '')

  return (
    <aside className="sidebar">
      <Link className="brand" to="/">UNIHELP</Link>

      <nav className="side-nav">
        <p className="side-nav__label">Help desk</p>
        <Link to="/" aria-current={location.pathname === '/' ? 'page' : undefined}>Home</Link>
        <Link to="/#topics">Browse FAQ</Link>

        {sections.map(section => (
          <Fragment key={section.label}>
            <p className="side-nav__label">{section.label}</p>
            {section.links.map(link => (
              <Link key={link.path} to={link.path}
                    aria-current={location.pathname === link.path ? 'page' : undefined}>
                {link.label}
                {link.path === '/notifications' && unread > 0 && (
                  <span className="nav-badge" aria-label={unread + ' unread'}>
                    {unread > 99 ? '99+' : unread}
                  </span>
                )}
              </Link>
            ))}
          </Fragment>
        ))}
      </nav>

      <div className="side-foot">
        <p className="side-user">
          <span>{(displayName || '').split(' ')[0] || 'Account'}</span>
          <span className="mono">{secondary}</span>
        </p>
        <button className="side-logout" type="button" onClick={handleSignOut}>Log out</button>
      </div>
    </aside>
  )
}
