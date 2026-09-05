import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { initials } from '../api.js'
import { useSession } from '../hooks/useSession.jsx'
import { Avatar } from './Bits.jsx'

/**
 * The landing page's top bar.
 *
 * It has two states. A guest sees Login and Register; someone signed in sees
 * an account chip that opens a menu with their details and the way into their
 * profile. Which one shows is decided by the session, not by the page.
 */
export function TopBar() {
  const { status, student, signOut } = useSession()
  const navigate = useNavigate()

  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const buttonRef = useRef(null)

  /* Close on a click anywhere else, and on Escape — the two things every user
     tries first. Both listeners are removed when the menu closes, so the page
     is not carrying handlers it does not need. */
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

  const signedIn = status === 'signedIn' && student
  const marks = signedIn ? initials(student.fullName) : '–'

  return (
    <header className="topbar">
      <Link className="topbar__brand" to="/">
        <span className="brand">UNIHELP</span>
        <span className="topbar__tag">University Help Desk</span>
      </Link>

      <nav className="topbar__nav">
        <a href="#topics">Browse FAQ</a>

        {/* While the session is still loading, neither state is shown. Flashing
            "Register" at someone who is already logged in looks broken. */}
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
              <Avatar marks={marks} src={student.profilePictureUrl} small />
              <span className="account__label">
                {(student.fullName || '').split(' ')[0] || 'My account'}
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
                    <Avatar marks={marks} src={student.profilePictureUrl} />
                    <div className="account__who">
                      <strong>{student.fullName || '(no name set)'}</strong>
                      <span className="mono">{student.studentId || ''}</span>
                      <span>{student.email || ''}</span>
                    </div>
                  </div>

                  <Link to="/profile" onClick={() => setOpen(false)}>My profile</Link>
                  <Link to="/profile#preferences" onClick={() => setOpen(false)}>Notification preferences</Link>
                  <Link to="/profile#activity" onClick={() => setOpen(false)}>Recent activity</Link>

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
