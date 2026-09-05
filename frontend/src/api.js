/**
 * The only place in the app that knows what the backend looks like.
 *
 * Keeping every URL in one object means a change to a @RequestMapping is a
 * one-line change here rather than a hunt through five components.
 */
export const API = {
  login:    '/api/auth/login',
  logout:   '/api/auth/logout',
  session:  '/api/students/me',         // who is logged in right now
  register: '/api/students',            // POST -> 201 Created
  student:  (id) => '/api/students/' + id
}

/**
 * A thin wrapper over fetch that does three jobs:
 *
 *  - sends the session cookie. Spring Security is session based, so without
 *    `credentials` every request after login would come back 403.
 *  - sets the JSON content type only when there is a body to send.
 *  - always returns the same shape, so callers never have to guess.
 *
 * @returns {Promise<{ok: boolean, status: number, data: any}>}
 */
export async function request(url, options = {}) {
  const config = {
    method: options.method || 'GET',
    credentials: 'same-origin',
    headers: {}
  }

  if (options.body !== undefined) {
    config.headers['Content-Type'] = 'application/json'
    config.body = JSON.stringify(options.body)
  }

  const response = await fetch(url, config)

  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }        // server sent plain text, not JSON
    }
  }

  return { ok: response.ok, status: response.status, data }
}

/**
 * Spring returns validation failures in several shapes depending on which
 * exception was thrown. This picks the most useful line out of any of them so
 * the student never sees a raw object or a bare status code.
 */
export function errorMessage(result, fallback) {
  const body = result.data

  if (body) {
    if (typeof body.message === 'string' && body.message) return body.message
    if (typeof body.error === 'string' && body.error) return body.error

    // A field-error map, e.g. { "email": "Must be a valid email address" }
    const firstField = Object.values(body).find(v => typeof v === 'string' && v)
    if (firstField) return firstField
  }

  if (result.status === 403) return 'You are not allowed to do that. Try logging in again.'
  if (result.status === 404) return 'We could not find that record.'

  return fallback || 'Something went wrong. Please try again.'
}

/* Spring's error body wraps its payload in these keys. None of them is a form
   field, and mistaking one for a field error is not harmless: the caller sees a
   non-empty map, decides the failure has been explained field by field, and
   never shows the actual message. That is exactly what hid the weak-password
   error — "path" is a string, so it was being read as a field error for an input
   named "path" that does not exist, and the real message was swallowed. */
const ENVELOPE_KEYS = new Set([
  'status', 'error', 'message', 'path', 'timestamp', 'trace', 'exception', 'errors'
])

/**
 * Pulls a field-error map out of a response, keyed by input name.
 *
 * @param {object} result       from request()
 * @param {string[]} knownFields the inputs this form actually has. Anything not
 *                               in the list is ignored, so a response shape we
 *                               did not anticipate degrades to "show the
 *                               message" rather than to silence.
 */
export function fieldErrors(result, knownFields) {
  const body = result.data
  if (!body || typeof body !== 'object') return {}

  const errors = {}
  Object.keys(body).forEach(key => {
    if (ENVELOPE_KEYS.has(key)) return
    if (knownFields && !knownFields.includes(key)) return
    if (typeof body[key] === 'string' && body[key]) errors[key] = body[key]
  })
  return errors
}

/** "Diroshaan Sivakaran" -> "DS", for the avatar circle. */
export function initials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  const first = parts[0] ? parts[0][0] : ''
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}
