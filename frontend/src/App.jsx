import { useEffect, useRef } from 'react'
import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { SessionProvider, useSession } from './hooks/useSession.jsx'
import { Link } from 'react-router-dom'
import { ROUTES, afterLogin, homeFor, loginFor } from './routes.jsx'
import './styles/app.css'

/**
 * HashRouter so a page refresh only ever asks Spring for index.html
 * (with BrowserRouter, refreshing /profile would 404 on the backend).
 */
export default function App() {
  return (
    <SessionProvider>
      <HashRouter>
        <ScrollToTop />
        <AnimatedRoutes />
      </HashRouter>
    </SessionProvider>
  )
}

// Scrolls to the top on every route change, or to the #anchor if the URL has one.
function ScrollToTop() {
  const { pathname, hash } = useLocation()
  const lastPath = useRef(pathname)

  useEffect(() => {
    const samePage = lastPath.current === pathname
    lastPath.current = pathname
    if (hash) {
      // After a page change the target isn't rendered yet (fade transition),
      // so poll every 50 ms for up to a second before giving up.
      let tries = 0
      const timer = setInterval(() => {
        const target = document.getElementById(hash.slice(1))
        if (target || ++tries > 20) {
          clearInterval(timer)
          if (target) target.scrollIntoView({ behavior: samePage ? 'smooth' : 'auto', block: 'start' })
        }
      }, 50)
      if (!samePage) window.scrollTo({ top: 0 })
      return () => clearInterval(timer)
    }
    window.scrollTo({ top: 0 })
  }, [pathname, hash])

  return null
}

/**
 * Route guard using the `access` value from routes.jsx. We wait until the
 * session check has finished, otherwise a signed-in user would be treated as a
 * guest and redirected on refresh.
 */
function Protected({ access, children }) {
  const { status, role } = useSession()
  const location = useLocation()

  if (status === 'loading') {
    return <div className="content"><div className="content-col"><p className="empty">Loading…</p></div></div>
  }

  if (access === 'public') return children

  // The page fading out after logout must not redirect to the login page;
  // the address bar already shows where the user is going.
  const showing = (window.location.hash.replace(/^#/, '').split('?')[0]) || '/'
  if (showing !== location.pathname && status !== 'signedIn') return null

  if (access === 'guest') {
    return status === 'signedIn' ? <Navigate to={afterLogin(role, location.search)} replace /> : children
  }

  // guests log in first and come back here via ?next=
  if (status !== 'signedIn') {
    return <Navigate to={loginFor(location.pathname + location.search)} replace />
  }

  if (!access.includes(role)) {
    return <Navigate to={homeFor(role)} replace />
  }

  return children
}

// Short cross-fade between pages.
function AnimatedRoutes() {
  const location = useLocation()

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={location.pathname}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.18, ease: [0.22, 0.61, 0.36, 1] }}
      >
        <Routes location={location}>
          {ROUTES.map(({ path, Component, access }) => (
            <Route key={path} path={path} element={
              <Protected access={access}><Component /></Protected>
            } />
          ))}

          <Route path="*" element={<NotFound />} />
        </Routes>
      </motion.div>
    </AnimatePresence>
  )
}

function NotFound() {
  const { status, role } = useSession()
  const home = status === 'signedIn' ? homeFor(role) : '/'
  return (
    <main className="not-found">
      <span className="code">404</span>
      <h1>We could not find that page.</h1>
      <p>The link may be out of date, or the page may have moved.</p>
      <div className="btn-row">
        <Link className="btn btn--primary" to={home}>{status === 'signedIn' ? 'Go to my home page' : 'Go to the help desk'}</Link>
      </div>
    </main>
  )
}
