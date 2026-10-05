import { useCallback, useEffect, useState } from 'react'
import { API, request } from '../api.js'

/* polled every 60 seconds, only while the tab is visible */
const POLL_MS = 60_000

/* Lets the Notifications page tell the sidebar badge to refresh after marking
   things read. */
const listeners = new Set()
export function announceUnreadChanged() {
  listeners.forEach(fn => fn())
}

/**
 * Unread notification count for the sidebar badge. Uses polling because the
 * backend has no push channel. Errors are ignored; the badge just keeps its
 * last value.
 */
export function useUnreadCount(enabled) {
  const [count, setCount] = useState(0)

  const refresh = useCallback(async () => {
    if (!enabled) return
    try {
      const result = await request(API.notificationsUnread)
      if (result.ok && result.data) {
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
