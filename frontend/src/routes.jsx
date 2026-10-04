import Welcome from './pages/Welcome.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Profile from './pages/Profile.jsx'
import DeleteAccount from './pages/DeleteAccount.jsx'
import Announcements from './pages/Announcements.jsx'
import Notifications from './pages/Notifications.jsx'
import ChangePassword from './pages/ChangePassword.jsx'
import OfficerProfile from './pages/officer/OfficerProfile.jsx'

import TicketList from './pages/student/TicketList.jsx'
import TicketNew from './pages/student/TicketNew.jsx'
import TicketDetail from './pages/student/TicketDetail.jsx'
import Bookmarks from './pages/student/Bookmarks.jsx'

import Queue from './pages/officer/Queue.jsx'
import QueueDetail from './pages/officer/QueueDetail.jsx'

import Articles from './pages/kb/Articles.jsx'
import ArticleDetail from './pages/kb/ArticleDetail.jsx'
import ArticleManage from './pages/kb/ArticleManage.jsx'
import ArticleEditor from './pages/kb/ArticleEditor.jsx'
import SavedArticles from './pages/kb/SavedArticles.jsx'

import AdminDashboard from './pages/admin/Dashboard.jsx'
import AdminUsers from './pages/admin/Users.jsx'
import AdminAnnouncements from './pages/admin/Announcements.jsx'

/**
 * One row per screen. App.jsx builds the routes from this and Sidebar.jsx builds
 * the nav links from the rows that have `nav`.
 * access: 'public' (anyone), 'guest' (signed out only) or a list of roles.
 */
export const ROUTES = [
  { path: '/', Component: Welcome, access: 'public' },
  { path: '/login', Component: Login, access: 'guest' },
  { path: '/register', Component: Register, access: 'guest' },

  { path: '/profile', Component: Profile, access: ['STUDENT'],
    nav: { section: 'My account', label: 'My profile' } },
  { path: '/delete-account', Component: DeleteAccount, access: ['STUDENT'] },
  // officers have their own profile page and endpoint (/api/officers/me)
  { path: '/officer/profile', Component: OfficerProfile, access: ['OFFICER'],
    nav: { section: 'My account', label: 'My profile' } },
  // Admins have no profile page, so change password gets its own nav link.
  { path: '/notifications', Component: Notifications, access: ['STUDENT', 'OFFICER', 'ADMIN'],
    nav: { section: 'My account', label: 'Notifications' } },
  { path: '/account/password', Component: ChangePassword, access: ['STUDENT', 'OFFICER', 'ADMIN'],
    nav: { section: 'My account', label: 'Change password' } },

  { path: '/tickets', Component: TicketList, access: ['STUDENT'],
    nav: { section: 'Tickets', label: 'My tickets' } },
  { path: '/tickets/new', Component: TicketNew, access: ['STUDENT'],
    nav: { section: 'Tickets', label: 'Submit a ticket' } },
  { path: '/tickets/:id', Component: TicketDetail, access: ['STUDENT'] },
  { path: '/bookmarks', Component: Bookmarks, access: ['STUDENT'],
    nav: { section: 'Tickets', label: 'Bookmarks' } },

  { path: '/kb', Component: Articles, access: ['STUDENT', 'OFFICER', 'ADMIN'],
    nav: { section: 'Knowledge base', label: 'Browse articles' } },
  { path: '/kb/saved', Component: SavedArticles, access: ['STUDENT'],
    nav: { section: 'Knowledge base', label: 'Saved articles' } },
  { path: '/kb/manage', Component: ArticleManage, access: ['OFFICER'],
    nav: { section: 'Knowledge base', label: 'Manage articles' } },
  { path: '/kb/manage/new', Component: ArticleEditor, access: ['OFFICER'] },
  { path: '/kb/manage/:id', Component: ArticleEditor, access: ['OFFICER'] },
  { path: '/kb/:id', Component: ArticleDetail, access: ['STUDENT', 'OFFICER', 'ADMIN'] },

  { path: '/queue', Component: Queue, access: ['OFFICER'],
    nav: { section: 'Support queue', label: 'Queue' } },
  { path: '/queue/:id', Component: QueueDetail, access: ['OFFICER'] },

  // Not under 'Help desk': Sidebar.jsx already draws that heading itself.
  { path: '/announcements', Component: Announcements, access: ['STUDENT', 'OFFICER', 'ADMIN'],
    nav: { section: 'Notices', label: 'Announcements' } },

  { path: '/admin/dashboard', Component: AdminDashboard, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Dashboard' } },
  { path: '/admin/users', Component: AdminUsers, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Users' } },
  { path: '/admin/announcements', Component: AdminAnnouncements, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Manage announcements' } }
]

/** Home screen for each role, used after login and when a page is off-limits. */
export function homeFor(role) {
  if (role === 'OFFICER') return '/queue'
  if (role === 'ADMIN') return '/admin/dashboard'
  return '/tickets'
}

/**
 * Reads ?next= from the login URL. Only in-app paths are allowed, so the login
 * page can't be used as an open redirect.
 */
export function nextFrom(search) {
  const next = new URLSearchParams(search || '').get('next')
  if (!next || !next.startsWith('/') || next.startsWith('//')) return null
  if (next.startsWith('/login') || next.startsWith('/register')) return null
  return next
}

/** After login: the ?next page if this role may see it, otherwise home. */
export function afterLogin(role, search) {
  const next = nextFrom(search)
  if (next) {
    const path = next.split('?')[0]
    const route = ROUTES.find(r => matches(r.path, path))
    if (route && (route.access === 'public' || (Array.isArray(route.access) && route.access.includes(role)))) {
      return next
    }
  }
  return homeFor(role)
}

export function loginFor(path) {
  return path && path !== '/' ? '/login?next=' + encodeURIComponent(path) : '/login'
}

/* '/tickets/:id' matches '/tickets/12'. */
function matches(pattern, path) {
  const a = pattern.split('/'), b = path.split('/')
  return a.length === b.length && a.every((part, i) => part.startsWith(':') || part === b[i])
}
