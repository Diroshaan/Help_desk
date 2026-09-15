import Welcome from './pages/Welcome.jsx'
import Login from './pages/Login.jsx'
import Register from './pages/Register.jsx'
import Profile from './pages/Profile.jsx'
import DeleteAccount from './pages/DeleteAccount.jsx'
import Announcements from './pages/Announcements.jsx'

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
 * One row per screen. App.jsx turns this into <Route> elements; Sidebar.jsx
 * turns the ones carrying a `nav` entry into the links for the signed-in
 * role(s) listed in `access`. Adding a screen is adding one object here, not
 * editing both files by hand.
 *
 * `access`:
 *   'public' — anyone, logged in or not
 *   'guest'  — only a signed-out visitor (App.jsx sends a signed-in user to
 *              their home screen instead of showing this one)
 *   [roles]  — only those roles; anyone else is sent to '/' (see Protected
 *              in App.jsx)
 */
export const ROUTES = [
  { path: '/', Component: Welcome, access: 'public' },
  { path: '/login', Component: Login, access: 'guest' },
  { path: '/register', Component: Register, access: 'guest' },

  { path: '/profile', Component: Profile, access: ['STUDENT'],
    nav: { section: 'My account', label: 'My profile' } },
  { path: '/delete-account', Component: DeleteAccount, access: ['STUDENT'] },

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

  // Every signed-in role, because an outage notice matters to staff as much
  // as to students.
  //
  // Its own section rather than 'Help desk': Sidebar.jsx renders that heading
  // itself for the Home and Browse FAQ links, and reusing the name here would
  // draw a second heading with the same text further down the nav.
  { path: '/announcements', Component: Announcements, access: ['STUDENT', 'OFFICER', 'ADMIN'],
    nav: { section: 'Notices', label: 'Announcements' } },

  { path: '/admin/dashboard', Component: AdminDashboard, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Dashboard' } },
  { path: '/admin/users', Component: AdminUsers, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Users' } },
  { path: '/admin/announcements', Component: AdminAnnouncements, access: ['ADMIN'],
    nav: { section: 'Administration', label: 'Manage announcements' } }
]

/** Where to send someone the instant we know their role — after login, and
 *  whenever a page they may not view sends them away instead of showing a
 *  403. */
export function homeFor(role) {
  if (role === 'OFFICER') return '/queue'
  if (role === 'ADMIN') return '/admin/dashboard'
  return '/profile'
}
