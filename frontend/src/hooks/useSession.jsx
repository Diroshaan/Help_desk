import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { API, onSessionLost, request } from '../api.js'

/**
 * Who is logged in, held once for the whole app.
 *
 * Every page needs this and none of them should ask separately — five pages
 * each calling the session endpoint would be five requests for one answer,
 * and they could disagree with each other. The provider asks once when the
 * app mounts and hands the result down.
 *
 * `status` is deliberately three-valued rather than a boolean:
 *
 *   'loading' — the answer has not come back yet
 *   'guest'   — nobody is logged in
 *   'signedIn'— `user` (and, for a student, `student`) holds the account
 *
 * Without the loading state a protected page would decide the visitor is a
 * guest during the first render and redirect them away before the server had
 * answered — which is the bug that made logging in look like it did nothing.
 *
 * WHY TWO ACCOUNT OBJECTS ('user' AND 'student')
 * -----------------------------------------------
 * GET /api/auth/me answers for any account type (STUDENT, OFFICER or ADMIN)
 * but only ever returns the thin CurrentUserResponse shape: id, email, role,
 * displayName. GET /api/students/me answers only for students, but returns
 * the full profile (phone, department, preferences, activity log) that
 * Profile.jsx needs. Calling the student-only endpoint for every role is what
 * used to make officers and admins look logged out the instant they signed
 * in — it 403'd, and that was read as "guest". So `user` is fetched first for
 * everyone, and `student` is fetched in addition only when user.role is
 * STUDENT.
 */
const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [status, setStatus] = useState('loading')
  const [user, setUser] = useState(null)       // CurrentUserResponse: id, email, role, displayName
  const [student, setStudent] = useState(null) // full StudentResponse — STUDENT accounts only

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
      /* Server unreachable. Treated as a guest so the public pages still
         render — a landing page with no API is still worth showing. */
    }

    setUser(null)
    setStudent(null)
    setStatus('guest')
    return null
  }, [])

  useEffect(() => { refresh() }, [refresh])

  /**
   * What to do when api.js reports the server no longer recognises us.
   *
   * Registered once, for the whole application, because the alternative is
   * every screen checking for an expired session itself — twenty-five places
   * to get right, and each one a chance to get it wrong differently.
   *
   * Deliberately NOT calling signOut(): that posts to /api/auth/logout, and
   * there is no session left to end. Posting anyway would be a pointless
   * request that fails, and the failure would look like a second error to
   * anyone reading the network tab.
   *
   * The redirect uses location.hash rather than useNavigate, because this
   * provider sits ABOVE HashRouter in the tree (see App.jsx) and so is outside
   * the router's context. Setting the hash directly is what a router-free
   * component has to do, and it works identically for a HashRouter.
   */
  useEffect(() => {
    onSessionLost(() => {
      setUser(null)
      setStudent(null)
      setStatus('guest')
      // Only redirect from a page that needed a session. Somebody reading the
      // public landing page should not be thrown to a login form because a
      // background request happened to fail.
      const path = window.location.hash.replace(/^#/, '')
      if (path && path !== '/' && !path.startsWith('/login') && !path.startsWith('/register')) {
        window.location.hash = '#/login?expired=1'
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
