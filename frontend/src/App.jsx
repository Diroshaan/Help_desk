import { useEffect } from 'react'
import { HashRouter, Route, Routes, useLocation } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { SessionProvider } from './hooks/useSession.jsx'
import Welcome from './pages/Welcome.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Profile from './pages/Profile.jsx'
import DeleteAccount from './pages/DeleteAccount.jsx'
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
          <Route path="/" element={<Welcome />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/delete-account" element={<DeleteAccount />} />

          {/* Anything unrecognised goes home rather than showing a blank page. */}
          <Route path="*" element={<Welcome />} />
        </Routes>
      </motion.div>
    </AnimatePresence>
  )
}
