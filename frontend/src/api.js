/**
 * The only place in the app that knows what the backend looks like.
 *
 * Keeping every URL in one object means a change to a @RequestMapping is a
 * one-line change here rather than a hunt through five components.
 */
export const API = {
  // Auth / session
  login:    '/api/auth/login',
  logout:   '/api/auth/logout',
  me:       '/api/auth/me',             // CurrentUserResponse for ANY role

  // Student profile (STUDENT only)
  session:  '/api/students/me',         // full StudentResponse, students only
  register: '/api/students',            // POST -> 201 Created
  students: '/api/students',            // GET (OFFICER/ADMIN) -> list
  student:  (id) => '/api/students/' + id,

  // Tickets (student side)
  ticketCategories: '/api/tickets/categories',
  tickets:          '/api/tickets',
  ticket:           (id) => '/api/tickets/' + id,
  ticketWithdraw:   (id) => '/api/tickets/' + id + '/withdraw',
  ticketSearch:     '/api/tickets/search',
  ticketAttachments:(id) => '/api/tickets/' + id + '/attachments',
  ticketAttachment: (id, attachmentId) => '/api/tickets/' + id + '/attachments/' + attachmentId,

  // Officer queue
  queue:           '/api/queue',
  queueTicket:     (id) => '/api/queue/' + id,
  queueStatus:     (id) => '/api/queue/' + id + '/status',
  queueAssign:     (id) => '/api/queue/' + id + '/assign',
  queueResolution: (id) => '/api/queue/' + id + '/resolution',
  queueNotes:      (id) => '/api/queue/' + id + '/notes',
  queueNote:       (id, noteId) => '/api/queue/' + id + '/notes/' + noteId,

  // Bookmarks & folders (tickets)
  bookmarks:       '/api/bookmarks',
  bookmark:        (id) => '/api/bookmarks/' + id,
  bookmarkFolder:  (id) => '/api/bookmarks/' + id + '/folder',
  bookmarkFolders: '/api/bookmark-folders',
  bookmarkFolderItem: (id) => '/api/bookmark-folders/' + id,

  // Feedback & archive (tickets)
  ticketFeedback: (id) => '/api/tickets/' + id + '/feedback',
  feedbackSummary: '/api/feedback/summary',
  ticketArchive:  (id) => '/api/tickets/' + id + '/archive',

  // Knowledge base
  articles:        '/api/articles',
  article:         (id) => '/api/articles/' + id,
  articlesManage:  '/api/articles/manage',
  articlePublish:  (id) => '/api/articles/' + id + '/publish',
  articleArchive:  (id) => '/api/articles/' + id + '/archive',
  articleRelated:  (id) => '/api/articles/' + id + '/related',
  articleRelatedItem: (id, relatedId) => '/api/articles/' + id + '/related/' + relatedId,
  articleBookmark: (id) => '/api/articles/' + id + '/bookmark',
  articlesBookmarked: '/api/articles/bookmarked',

  // Admin
  announcements:      '/api/announcements',            // live feed, any role
  adminAnnouncements: '/api/admin/announcements',
  adminAnnouncement:  (id) => '/api/admin/announcements/' + id,
  adminDashboard:     '/api/admin/dashboard',
  adminUsers:         '/api/admin/users',
  adminUserStatus:    (id) => '/api/admin/users/' + id + '/status',
  adminUser:          (id) => '/api/admin/users/' + id,
  adminOfficers:      '/api/admin/officers',
  adminAdministrators:'/api/admin/administrators',

  // Reference data
  departments:           '/api/departments',
  categories:            '/api/categories',
  departmentCategories:  (code) => '/api/departments/' + code + '/categories'
}

/**
 * Turns { a: 1, b: undefined, c: '' } into '?a=1&c=' — keys whose value is
 * undefined or null are dropped, so callers can pass optional filters
 * without building the query string by hand.
 */
export function withQuery(url, params) {
  if (!params) return url
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    search.set(key, value)
  })
  const qs = search.toString()
  return qs ? url + '?' + qs : url
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
  return readResponse(response)
}

/**
 * Same contract as request(), but sends a FormData body instead of JSON —
 * for the two multipart endpoints (ticket attachments, queue resolutions).
 * Never set Content-Type by hand on a FormData request: the browser has to
 * append the multipart boundary itself, and a hand-set header omits it.
 */
export async function requestForm(url, { method = 'POST', form } = {}) {
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    body: form
  })
  return readResponse(response)
}

async function readResponse(response) {
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

/** Renders an ISO-ish LocalDateTime string as something a person reads
 *  comfortably. ActivityLogResponse.timestamp is already pre-formatted by the
 *  backend and must NOT go through this — this is for the plain
 *  LocalDateTime fields (createdAt, updatedAt, ...) most other DTOs return. */
export function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}
