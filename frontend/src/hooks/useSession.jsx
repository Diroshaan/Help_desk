import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { API, request } from '../api.js'

/**
 * Who is logged in, held once for the whole app.
 *
 * Every page needs this and none of them should ask separately — five pages
 * each calling /api/students/me would be five requests for one answer, and
 * they could disagree with each other. The provider asks once when the app
 * mounts and hands the result down.
 *
 * `status` is deliberately three-valued rather than a boolean:
 *
 *   'loading' — the answer has not come back yet
 *   'guest'   — nobody is logged in
 *   'signedIn'— `student` holds the account
 *
 * Without the loading state a protected page would decide the visitor is a
 * guest during the first render and redirect them away before the server had
 * answered — which is the bug that made logging in look like it did nothing.
 */
const SessionContext = createContext(null)

export function SessionProvider({ children }) {
  const [status, setStatus] = useState('loading')
  const [student, setStudent] = useState(null)

  const refresh = useCallback(async () => {
    try {
      const result = await request(API.session)

      // A guest gets 403 here: SecurityConfig enables neither formLogin nor
      // httpBasic, so Spring answers an anonymous request with 403, not 401.
      if (result.ok && result.data && result.data.id) {
        setStudent(result.data)
        setStatus('signedIn')
        return result.data
      }
    } catch {
      /* Server unreachable. Treated as a guest so the public pages still
         render — a landing page with no API is still worth showing. */
    }

    setStudent(null)
    setStatus('guest')
    return null
  }, [])

  useEffect(() => { refresh() }, [refresh])

  const signOut = useCallback(async () => {
    try {
      await request(API.logout, { method: 'POST' })
    } finally {
      setStudent(null)
      setStatus('guest')
    }
  }, [])

  return (
    <SessionContext.Provider value={{ status, student, setStudent, refresh, signOut }}>
      {children}
    </SessionContext.Provider>
  )
}

export function useSession() {
  const value = useContext(SessionContext)
  if (!value) throw new Error('useSession must be used inside a SessionProvider')
  return value
}
