import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { API, onSessionLost, request } from '../api.js'

/**
 * Holds the logged-in user for the whole app, fetched once on load.
 * status is 'loading', 'guest' or 'signedIn'; the loading state stops pages
 * redirecting before the server has answered.
 * `user` comes from /api/auth/me (any role). Students also get `student`, the
 * full profile from /api/students/me, which other roles can't call.
 */
const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [status, setStatus] = useState('loading')
  const [user, setUser] = useState(null)       // id, email, role, displayName
  const [student, setStudent] = useState(null) // students only

  const refresh = useCallback(async () => {
    try {
      const result = await request(API.me)

      if (result.ok && result.data && result.data.id) {
        setUser(result.data)
        setStatus('signedIn')

        if (result.data.role === 'STUDENT') {
          const studentResult = await request(API.session)
          setStudent(studentResult.ok ? studentResult.data : null)
        } else {
          setStudent(null)
        }

        return result.data
      }
    } catch {
      /* server unreachable - treat as guest so public pages still show */
    }

    setUser(null)
    setStudent(null)
    setStatus('guest')
    return null
  }, [])

  useEffect(() => { refresh() }, [refresh])

  /**
   * Called by api.js when the session has expired. No signOut() here because
   * there is no session left to log out of. We set location.hash directly since
   * this provider sits above the router and can't use useNavigate.
   */
  useEffect(() => {
    onSessionLost(() => {
      setUser(null)
      setStudent(null)
      setStatus('guest')
      // don't send people on public pages to the login form
      const path = window.location.hash.replace(/^#/, '')
      if (path && path !== '/' && !path.startsWith('/login') && !path.startsWith('/register')) {
        window.location.hash = '#/login?expired=1&next=' + encodeURIComponent(path)
      }
    })
  }, [])

  const signOut = useCallback(async () => {
    try {
      await request(API.logout, { method: 'POST' })
    } finally {
      setUser(null)
      setStudent(null)
      setStatus('guest')
    }
  }, [])

  const role = user ? user.role : null

  return (
    <SessionContext.Provider value={{ status, user, role, student, setStudent, refresh, signOut }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useSession must be used inside a SessionProvider')
  return value
}
