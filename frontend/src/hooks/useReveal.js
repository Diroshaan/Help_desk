import { useEffect, useRef, useState } from 'react'

/**
 * Returns [ref, shown]: shown turns true the first time the element scrolls
 * into view. Runs once only. Without IntersectionObserver everything is shown
 * straight away so nothing stays hidden.
 */
export function useReveal(options = {}) {
  const ref = useRef(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const element = ref.current
    if (!element) return

    if (!('IntersectionObserver' in window)) {
      setShown(true)
      return
    }

    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return
        setShown(true)
        observer.unobserve(entry.target)
      })
    }, {
      threshold: options.threshold ?? 0.12,
      rootMargin: options.rootMargin ?? '0px 0px -40px 0px'
    })

    observer.observe(element)
    return () => observer.disconnect()
  }, [options.threshold, options.rootMargin])

  return [ref, shown]
}
