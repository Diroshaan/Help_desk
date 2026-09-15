import { useEffect } from 'react'
import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { SessionProvider, useSession } from './hooks/useSession.jsx'
import { ROUTES, homeFor } from './routes.jsx'
import './styles/app.css'

/**
 * HashRouter rather than BrowserRouter.
 *
 * With browser routing, a refresh on /profile sends GET /profile to Spring,
 * which has no controller for it and answers 404 — fixing that needs a
 * catch-all forward added to the backend. Hash routing keeps every path after
 * the '#', so the server only ever sees a request for index.html and no Spring
 * change is needed at all. The URLs read as /#/profile.
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

/**
 * A single-page app keeps the scroll position when the route changes, which
 * lands you halfway down a page you have never seen. This puts it back to the
 * top — except when the URL carries an anchor, where jumping to that element
 * is the whole point.
 */
function ScrollToTop() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    if (hash) {
      const target = document.getElementById(hash.slice(1))
      if (target) {
        target.scrollIntoView({ behavior: 'smooth' })
        return
      }
    }
    window.scrollTo({ top: 0 })
  }, [pathname, hash])

  return null
}

/**
 * Gate around one route's element, driven by that route's `access` entry in
 * routes.jsx:
 *
 *   'public' — render for anyone
 *   'guest'  — render only while signed out; a signed-in visitor is sent to
 *              their own home screen instead (so a logged-in officer hitting
 *              /login lands on the queue, not the login form)
 *   [roles]  — render only for those roles; anyone else — guest or the wrong
 *              role — is sent to '/'
 *
 * Waiting for `status !== 'loading'` before deciding is what stops a
 * protected page from reading a not-yet-answered session as "guest" and
 * redirecting away before the server had a chance to say otherwise.
 */
function Protected({ access, children }) {
  const { status, role } = useSession()

  if (status === 'loading') {
    return <div className="content"><div className="content-col"><p className="empty">Loading…</p></div></div>
  }

  if (access === 'public') return children

  if (access === 'guest') {
    return status === 'signedIn' ? <Navigate to={homeFor(role)} replace /> : children
  }

  if (status !== 'signedIn' || !access.includes(role)) {
    return <Navigate to="/" replace />
  }

  return children
}

/**
 * Pages cross-fade instead of snapping. 180ms is short enough that it reads as
 * responsiveness rather than as an animation you are waiting for.
 */
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

          {/* Anything unrecognised goes home rather than showing a blank page. */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </motion.div>
    </AnimatePresence>
  )
}
