import { Fragment } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useSession } from '../hooks/useSession.jsx'
import { ROUTES } from '../routes.jsx'

/**
 * The teal sidebar every signed-in screen shares — extracted from what used
 * to be Profile.jsx's own local component. Which links appear below "Help
 * desk" depends on the signed-in role: a route only shows up here if it
 * carries a `nav` entry in routes.jsx AND the current role is in that
 * route's `access` list, so adding a screen to routes.jsx is enough to put
 * it in the sidebar too — no second place to remember.
 */
export function Sidebar() {
  const { role, user, student, signOut } = useSession()
  const navigate = useNavigate()
  const location = useLocation()

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
    await signOut()
    navigate('/')
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
