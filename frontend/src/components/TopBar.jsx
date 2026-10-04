import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { initials } from '../api.js'
import { useSession } from '../hooks/useSession.jsx'
import { homeFor } from '../routes.jsx'
import { Avatar } from './Bits.jsx'

/** Landing page top bar: Login/Register for guests, an account menu when signed in. */
export function TopBar() {
  const { status, user, student, signOut } = useSession()
  const navigate = useNavigate()

  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const buttonRef = useRef(null)

  /* close the menu on an outside click or Escape */
  useEffect(() => {
    if (!open) return

    function onDocumentClick(event) {
      if (wrapRef.current && !wrapRef.current.contains(event.target)) setOpen(false)
    }

    function onKeyDown(event) {
      if (event.key !== 'Escape') return
      setOpen(false)
      buttonRef.current?.focus()
    }

    document.addEventListener('click', onDocumentClick)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('click', onDocumentClick)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  async function handleSignOut() {
    setOpen(false)
    await signOut()
    navigate('/')
  }

  const signedIn = status === 'signedIn' && user
  const displayName = student?.fullName || user?.displayName || ''
  const marks = signedIn ? initials(displayName) : '–'
  const homeLink = homeFor(user?.role)

  return (
    <header className="topbar">
      <Link className="topbar__brand" to="/">
        <span className="brand">UNIHELP</span>
        <span className="topbar__tag">University Help Desk</span>
      </Link>

      <nav className="topbar__nav">
        {/* Not a plain <a href="#topics">: with HashRouter that would be read as
            a route. ScrollToTop handles the anchor. */}
        <Link to="/#topics">Browse FAQ</Link>

        {/* nothing shows while the session is still loading */}
        {status === 'guest' && (
          <>
            <Link to="/login">Login</Link>
            <Link className="btn btn--primary" to="/register">Register</Link>
          </>
        )}

        {signedIn && (
          <div className="account" ref={wrapRef}>
            <button
              className="account__btn"
              type="button"
              ref={buttonRef}
              aria-expanded={open}
              aria-haspopup="true"
              onClick={() => setOpen(value => !value)}
            >
              <Avatar marks={marks} src={student?.profilePictureUrl} small />
              <span className="account__label">
                {displayName.split(' ')[0] || 'My account'}
              </span>
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" aria-hidden="true"
                   stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
                <path d="M5 9l7 7 7-7" />
              </svg>
            </button>

            <AnimatePresence>
              {open && (
                <motion.div
                  className="account__menu"
                  initial={{ opacity: 0, y: -6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ duration: 0.16, ease: [0.22, 0.61, 0.36, 1] }}
                >
                  <div className="account__head">
                    <Avatar marks={marks} src={student?.profilePictureUrl} />
                    <div className="account__who">
                      <strong>{displayName || '(no name set)'}</strong>
                      {student?.studentId && <span className="mono">{student.studentId}</span>}
                      <span>{user?.email || ''}</span>
                    </div>
                  </div>

                  {student ? (
                    <>
                      <Link to="/profile" onClick={() => setOpen(false)}>My profile</Link>
                      <Link to="/profile#preferences" onClick={() => setOpen(false)}>Notification preferences</Link>
                      <Link to="/profile#activity" onClick={() => setOpen(false)}>Recent activity</Link>
                    </>
                  ) : (
                    <>
                      <Link to={homeLink} onClick={() => setOpen(false)}>Go to my dashboard</Link>
                      {user?.role === 'OFFICER' && (
                        <Link to="/officer/profile" onClick={() => setOpen(false)}>My profile</Link>
                      )}
                    </>
                  )}
                  <Link to="/notifications" onClick={() => setOpen(false)}>Notifications</Link>
                  <Link to="/account/password" onClick={() => setOpen(false)}>Change password</Link>

                  <hr />
                  <button type="button" className="danger" onClick={handleSignOut}>Log out</button>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        )}
      </nav>
    </header>
  )
}
