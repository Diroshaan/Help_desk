import { useEffect, useRef, useState } from 'react'

/**
 * Reveals an element the first time it scrolls into view.
 *
 * Returns a ref to attach and a boolean to drive the class:
 *
 *   const [ref, shown] = useReveal()
 *   <section ref={ref} className={'reveal' + (shown ? ' is-visible' : '')}>
 *
 * It unobserves as soon as it fires, so the animation happens once rather than
 * every time the student scrolls past. Browsers without IntersectionObserver
 * are shown everything immediately — an effect that fails should never leave
 * content invisible.
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
