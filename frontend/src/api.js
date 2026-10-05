/** All backend URLs in one place, plus the fetch helpers the pages use. */
export const API = {
  // Auth / session
  login:    '/api/auth/login',
  logout:   '/api/auth/logout',
  me:       '/api/auth/me',             // works for any role
  password: '/api/auth/password',

  // Notifications inbox (any signed-in role)
  notifications:        '/api/notifications',
  notificationsUnread:  '/api/notifications/unread-count',
  notificationRead:     (id) => '/api/notifications/' + id + '/read',
  notificationsReadAll: '/api/notifications/read-all',

  // Officer's own profile
  officerMe: '/api/officers/me',

  // Student profile (STUDENT only)
  session:  '/api/students/me',
  register: '/api/students',            // POST
  students: '/api/students',            // GET, officers and admins
  student:  (id) => '/api/students/' + id,
  studentAvatar: (id) => '/api/students/' + id + '/avatar',

  // Tickets (student side)
  ticketCategories: '/api/tickets/categories',
  tickets:          '/api/tickets',
  ticket:           (id) => '/api/tickets/' + id,
  ticketWithdraw:   (id) => '/api/tickets/' + id + '/withdraw',
  ticketSearch:     '/api/tickets/search',
  ticketAttachments:(id) => '/api/tickets/' + id + '/attachments',
  ticketAttachment: (id, attachmentId) => '/api/tickets/' + id + '/attachments/' + attachmentId,
  ticketHistory:    (id) => '/api/tickets/' + id + '/history',
  ticketResolution: (id) => '/api/tickets/' + id + '/resolution',
  ticketResolutionFile: (id) => '/api/tickets/' + id + '/resolution/attachment',
  ticketsArchived:  '/api/tickets/archived',

  // Officer queue
  queue:           '/api/queue',
  queueTicket:     (id) => '/api/queue/' + id,
  queueStatus:     (id) => '/api/queue/' + id + '/status',
  queueAssign:     (id) => '/api/queue/' + id + '/assign',
  queueResolution: (id) => '/api/queue/' + id + '/resolution',
  queueNotes:      (id) => '/api/queue/' + id + '/notes',
  queueNote:       (id, noteId) => '/api/queue/' + id + '/notes/' + noteId,
  queueHistory:    (id) => '/api/queue/' + id + '/history',
  queueAttachments:(id) => '/api/queue/' + id + '/attachments',
  queueAttachment: (id, attachmentId) => '/api/queue/' + id + '/attachments/' + attachmentId,
  queueResolutionFile: (id) => '/api/queue/' + id + '/resolution/attachment',

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
  adminOfficerDepartments: (id) => '/api/admin/officers/' + id + '/departments',
  adminOfficerSupervisor: (id) => '/api/admin/officers/' + id + '/supervisor',

  // Reference data
  departments:           '/api/departments',
  categories:            '/api/categories',
  departmentCategories:  (code) => '/api/departments/' + code + '/categories'
}

/** Adds a query string, skipping empty values, so optional filters can be passed straight in. */
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
 * Wrapper over fetch. Sends the session cookie (login is session based) and
 * always resolves to { ok, status, data }.
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
  return readResponse(response, url)
}

/**
 * Like request() but for multipart uploads. Content-Type is left to the browser
 * so it can add the multipart boundary.
 */
export async function requestForm(url, { method = 'POST', form } = {}) {
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    body: form
  })
  return readResponse(response, url)
}

/* Session expiry. A 401 means the session is gone. A 403 is ambiguous (Spring
   also sends it for anonymous requests), so we confirm with /api/auth/me first
   and only report a lost session if that returns 401. useSession decides what
   to do about it. */

let sessionLostHandler = null

export function onSessionLost(handler) {
  sessionLostHandler = handler
}

// /me is the check itself, and a failed login is just a wrong password.
const AUTH_PATHS = ['/api/auth/me', '/api/auth/login', '/api/auth/logout']

let verifying = false        // one check at a time

async function checkSessionLost(url, status) {
  if (!sessionLostHandler) return
  if (AUTH_PATHS.some(path => url.startsWith(path))) return

  if (status === 401) {
    sessionLostHandler()
    return
  }

  if (status === 403) {
    if (verifying) return
    verifying = true
    try {
      const check = await fetch(API.me, { credentials: 'same-origin' })
      if (check.status === 401) sessionLostHandler()
    } catch {
      /* server unreachable - not a session problem */
    } finally {
      verifying = false
    }
  }
}

async function readResponse(response, url) {
  let data = null
  const text = await response.text()
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = { message: text }        // plain text, not JSON
    }
  }

  if (!response.ok) checkSessionLost(url, response.status)

  return { ok: response.ok, status: response.status, data }
}

/** Picks a readable message out of the different error shapes Spring returns. */
export function errorMessage(result, fallback) {
  const body = result.data

  if (body) {
    if (typeof body.message === 'string' && body.message) return body.message
    if (typeof body.error === 'string' && body.error) return body.error

    // field-error map, e.g. { "email": "Must be a valid email address" }
    const firstField = Object.values(body).find(v => typeof v === 'string' && v)
    if (firstField) return firstField
  }

  if (result.status === 403) return 'You are not allowed to do that. Try logging in again.'
  if (result.status === 404) return 'We could not find that record.'

  return fallback || 'Something went wrong. Please try again.'
}

/* Keys of Spring's error body. They are not form fields, so they must not be
   read as field errors (otherwise the real message gets hidden). */
const ENVELOPE_KEYS = new Set([
  'status', 'error', 'message', 'path', 'timestamp', 'trace', 'exception', 'errors'
])

/** Field errors from a response, limited to the form's own input names. */
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

/* Uploads: same type and 5 MB rules as the server, checked here first for a
   quick message. The server still checks the real file content. */
export const UPLOAD_ACCEPT = '.pdf,.png,.jpg,.jpeg,.gif,.webp,application/pdf,image/png,image/jpeg,image/gif,image/webp'
export const UPLOAD_MAX_BYTES = 5 * 1024 * 1024
const UPLOAD_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp']

/** null if the file is fine, otherwise the error to show. */
export function uploadProblem(file) {
  if (!file) return null
  const extension = (file.name.split('.').pop() || '').toLowerCase()
  if (!UPLOAD_EXTENSIONS.includes(extension)) {
    return 'Only PDF and image files (PNG, JPEG, GIF, WEBP) can be attached.'
  }
  if (file.size > UPLOAD_MAX_BYTES) {
    return 'That file is ' + formatBytes(file.size) + '. Files can be at most 5 MB.'
  }
  return null
}

/** 81234 -> "79 KB", 2400000 -> "2.3 MB". */
export function formatBytes(bytes) {
  if (bytes === null || bytes === undefined) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return Math.round(bytes / 1024) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

/** "Diroshaan Sivakaran" -> "DS", for the avatar circle. */
export function initials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  const first = parts[0] ? parts[0][0] : ''
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

/** Formats a LocalDateTime string. Not for the activity log timestamp, which
 *  the backend already formats. */
export function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('en-GB', {
    day: 'numeric', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  })
}
