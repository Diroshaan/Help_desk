import { useCallback, useEffect, useState } from 'react'
import { API, request } from '../api.js'

/* Every 60 seconds while the tab is visible. The count is a single COUNT(*)
   on an indexed column, so this is cheap; the interval is about not waking a
   backgrounded laptop, not about server load. */
const POLL_MS = 60_000

/* A tiny in-page broadcast so the Notifications page can tell the sidebar
   "I just marked things read" without a shared context provider. The sidebar
   is re-rendered on every route anyway, so this only matters for changes made
   while staying on one page. */
const listeners = new Set()
export function announceUnreadChanged() {
  listeners.forEach(fn => fn())
}

/**
 * The signed-in user's unread notification count, for the badge in the
 * sidebar (GET /api/notifications/unread-count -> { unread }).
 *
 * Polling, not WebSockets or server-sent events: the backend has no push
 * channel, and adding one for a badge would be a lot of moving parts for a
 * number that is allowed to be a minute out of date. Polling pauses while the
 * tab is hidden and refreshes the moment it comes back, which is when the
 * user would actually look at the badge.
 *
 * Failures are swallowed on purpose: a badge that cannot load shows nothing,
 * rather than an error banner on every page for a decoration.
 */
export function useUnreadCount(enabled) {
  const [count, setCount] = useState(0)

  const refresh = useCallback(async () => {
    if (!enabled) return
    try {
      const result = await request(API.notificationsUnread)
      if (result.ok && result.data) {
        // NotificationController answers { "unread": n }.
        const value = Number(result.data.unread ?? 0)
        setCount(Number.isFinite(value) ? value : 0)
      }
    } catch {
      /* server unreachable - keep the last known count */
    }
  }, [enabled])

  useEffect(() => {
    if (!enabled) { setCount(0); return }
    refresh()

    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') refresh()
    }, POLL_MS)
    const onVisible = () => { if (document.visibilityState === 'visible') refresh() }
    document.addEventListener('visibilitychange', onVisible)
    listeners.add(refresh)

    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
      listeners.delete(refresh)
    }
  }, [enabled, refresh])

  return count
}
