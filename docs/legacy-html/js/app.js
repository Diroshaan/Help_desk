/* ==============================================================================
   SUPERSEDED — kept for reference only.

   UNIHELP · SE2030 Web-Based Help Desk System · Group MLBB8G204
   F1 — Student Profile & Preferences Management · Diroshaan S (IT25101580)

   The shared helpers for the plain-HTML pages: the API endpoint table, the
   fetch wrapper, the notice and field-error helpers, initials() and logout().

   REPLACED BY
     frontend/src/api.js               — API, request(), errorMessage(),
                                         fieldErrors(), initials()
     frontend/src/hooks/useSession.jsx — the session, including sign-out

   The logic is the same; only the delivery changed. request() is almost
   identical in both, because the reasons behind it did not change: Spring
   Security is session based, so every call must send its cookie, and callers
   should never have to guess what shape a response came back in.

   What React did change is WHERE the answer to "who is logged in?" lives. Here,
   every page asked the server for itself — five pages, five requests, five
   chances to disagree. In the React app one context asks once and shares it.

   Loaded only by the old .html pages. The React bundle does not touch it.
   ============================================================================== */

/* ==========================================================================
   UNIHELP — shared front-end helpers
   F1: Student Profile & Preferences Management

   Every page loads this file before its own <script>. It holds the three
   things all the pages need: where the API lives, how to call it, and how
   to show a message. Nothing else is global.
   ========================================================================== */

/* --------------------------------------------------------------------------
   1. API endpoints
   Kept in one object so a change to a controller's @RequestMapping is a
   one-line change here rather than a hunt through five HTML files.
   -------------------------------------------------------------------------- */
const API = {
    login:    '/api/auth/login',
    logout:   '/api/auth/logout',
    me:       '/api/auth/session',             // returns the logged-in student
    register: '/api/students',                 // POST -> 201 Created
    student:  (id) => '/api/students/' + id    // GET / PUT / DELETE
};

/* --------------------------------------------------------------------------
   2. request()
   A thin wrapper over fetch. It does three jobs:
     - sends the session cookie (Spring Security is session based, so without
       credentials every request after login would come back 403)
     - sets the JSON content type only when there is a body to send
     - always returns the same shape, so callers never have to guess

   Returns { ok, status, data } where data is the parsed JSON body, or null
   when the response has no body (204 No Content, for example).
   -------------------------------------------------------------------------- */
async function request(url, options = {}) {
    const config = {
        method: options.method || 'GET',
        credentials: 'same-origin',
        headers: {}
    };

    if (options.body !== undefined) {
        config.headers['Content-Type'] = 'application/json';
        config.body = JSON.stringify(options.body);
    }

    const response = await fetch(url, config);

    let data = null;
    const text = await response.text();
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (e) {
            data = { message: text };   // server returned plain text, not JSON
        }
    }

    return { ok: response.ok, status: response.status, data: data };
}

/* --------------------------------------------------------------------------
   3. showNotice()
   Puts a message into the notice block on the page and makes it visible.
   kind is 'info' (default), 'error' or 'warn' — it only changes the colour
   of the bar down the left edge.
   -------------------------------------------------------------------------- */
function showNotice(element, message, kind = 'info') {
    if (!element) return;
    element.className = 'notice' + (kind === 'info' ? '' : ' notice--' + kind);
    element.textContent = message;
    element.hidden = false;
}

function hideNotice(element) {
    if (element) element.hidden = true;
}

/* --------------------------------------------------------------------------
   4. errorMessage()
   Spring returns validation failures in a few different shapes depending on
   which exception was thrown. This picks the most useful line out of any of
   them so the user never sees a raw object or a bare status code.
   -------------------------------------------------------------------------- */
function errorMessage(result, fallback) {
    const body = result.data;

    if (body) {
        if (typeof body.message === 'string' && body.message) return body.message;
        if (typeof body.error === 'string' && body.error)     return body.error;

        // A field-error map, e.g. { "email": "Must be a valid email address" }
        const firstField = Object.values(body).find(v => typeof v === 'string' && v);
        if (firstField) return firstField;
    }

    if (result.status === 403) return 'You are not allowed to do that. Try logging in again.';
    if (result.status === 404) return 'We could not find that record.';

    return fallback || 'Something went wrong. Please try again.';
}

/* --------------------------------------------------------------------------
   5. fieldErrors()
   Marks individual inputs as invalid when the server sends back a field-error
   map. Falls back silently when the response is not that shape.
   -------------------------------------------------------------------------- */
function clearFieldErrors(form) {
    form.querySelectorAll('.field.is-invalid').forEach(f => f.classList.remove('is-invalid'));
    form.querySelectorAll('.field-error').forEach(e => { e.textContent = ''; });
}

function applyFieldErrors(form, body) {
    if (!body || typeof body !== 'object') return false;

    let applied = false;
    Object.keys(body).forEach(name => {
        const input = form.querySelector('[name="' + name + '"]');
        if (!input || typeof body[name] !== 'string') return;

        const field = input.closest('.field');
        if (!field) return;

        field.classList.add('is-invalid');
        const slot = field.querySelector('.field-error');
        if (slot) slot.textContent = body[name];
        applied = true;
    });
    return applied;
}

/* --------------------------------------------------------------------------
   6. initials()
   Turns "Diroshaan Sivakaran" into "DS" for the avatar circle, so a student
   who has not uploaded a picture still gets something personal.
   -------------------------------------------------------------------------- */
function initials(name) {
    if (!name) return '?';
    const parts = name.trim().split(/\s+/);
    const first = parts[0] ? parts[0][0] : '';
    const last  = parts.length > 1 ? parts[parts.length - 1][0] : '';
    return (first + last).toUpperCase();
}

/* --------------------------------------------------------------------------
   7. logout()
   Used by the sidebar button. Calls the endpoint, then sends the visitor to
   the welcome page whether or not the call succeeded — if the session is
   already gone there is nothing to keep them here for.
   -------------------------------------------------------------------------- */
async function logout() {
    try {
        await request(API.logout, { method: 'POST' });
    } finally {
        window.location.href = 'welcome.html';
    }
}
