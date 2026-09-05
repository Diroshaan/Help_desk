import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { TopBar } from '../components/TopBar.jsx'
import { Notice } from '../components/Bits.jsx'
import { useReveal } from '../hooks/useReveal.js'
import { useSession } from '../hooks/useSession.jsx'

/* The three ways into the help desk, and the chapters of the walkthrough.
   Kept as data so the markup below stays about layout, not content. */
const ROUTES = [
  {
    num: '01',
    title: 'Search the Knowledge Base',
    body: 'Guides, policies and step-by-steps from every department. No account needed.',
    action: 'Browse articles',
    to: '#topics',
    gated: false
  },
  {
    num: '02',
    title: 'Submit a Ticket',
    body: 'Describe the problem once. It is routed and tracked against your Student ID.',
    action: 'Submit a ticket',
    to: '/register',
    signedInTo: '/profile',
    gated: true
  },
  {
    num: '03',
    title: 'Track Your Request',
    body: 'Follow every status change, officer response and resolution in one thread.',
    action: 'Track requests',
    to: '/register',
    signedInTo: '/profile',
    gated: true
  }
]

const CHAPTERS = [
  { at: 0,   label: 'Create your account',   stamp: '0:00' },
  { at: 24,  label: 'Search before you ask', stamp: '0:24' },
  { at: 52,  label: 'Raise a request',       stamp: '0:52' },
  { at: 78,  label: 'Follow it to resolved', stamp: '1:18' },
  { at: 104, label: 'Save it to a folder',   stamp: '1:44' }
]

const TOPICS = [
  'Reset my portal password', 'Connect to eduroam', 'Request an official transcript',
  'Fee payment methods', 'Library loan limits', 'Change my registered modules',
  'Hostel maintenance requests', 'Exam re-sit application'
]

const DESKS = [
  { name: 'IT Services',   email: 'itdesk@university.lk',    phone: '+94 11 000 0001' },
  { name: 'Registration',  email: 'registrar@university.lk', phone: '+94 11 000 0002' },
  { name: 'Financial Aid', email: 'finaid@university.lk',    phone: '+94 11 000 0003' }
]

/* A branded still for the player, drawn rather than shipped as a file. */
const POSTER =
  "data:image/svg+xml;utf8," + encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1600 900">
       <rect width="1600" height="900" fill="#10322c"/>
       <text x="800" y="470" font-family="Inter,Arial,sans-serif" font-size="54" font-weight="700"
             letter-spacing="6" fill="#ffffff" text-anchor="middle">UNIHELP</text>
       <text x="800" y="530" font-family="Inter,Arial,sans-serif" font-size="24"
             letter-spacing="3" fill="#c3ded6" text-anchor="middle">HOW IT WORKS</text>
     </svg>`)

export default function Welcome() {
  const { status, student } = useSession()
  const signedIn = status === 'signedIn' && student

  const [query, setQuery] = useState('')
  const [searchNote, setSearchNote] = useState('')

  const [routesRef, routesShown] = useReveal()
  const [howtoRef, howtoShown] = useReveal()
  const [topicsRef, topicsShown] = useReveal()
  const [ctaRef, ctaShown] = useReveal()

  function runSearch(term) {
    const text = (term ?? query).trim()

    if (!text) {
      setSearchNote('Type a few words about your problem, or pick one of the topics below.')
      return
    }

    // The knowledge base is F5 and is not built yet. Saying what will happen
    // beats a button that appears broken.
    setSearchNote(
      'Knowledge base search arrives with the FAQ portal in Sprint 4. In the meantime, ' +
      'submit a ticket describing “' + text + '” and it will reach the right desk.'
    )
  }

  return (
    <>
      <TopBar />

      {/* ============ Hero ============ */}
      <section className="hero">
        <div className="hero__inner rise rise-1">
          <p className="eyebrow">Student support · One place</p>

          <h1>Get help from your university, in one place.</h1>

          <p className="hero__lede">
            Search the knowledge base first — most questions are already answered.
            If not, submit a ticket and it routes straight to the right department.
          </p>

          <form className="searchbar" role="search"
                onSubmit={event => { event.preventDefault(); runSearch() }}>
            <span className="searchbar__icon" aria-hidden="true">
              <svg width="19" height="19" viewBox="0 0 24 24" fill="none"
                   stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
                <circle cx="11" cy="11" r="7" />
                <path d="M20 20l-3.6-3.6" />
              </svg>
            </span>
            <label className="sr-only" htmlFor="q">Search FAQs and self-service guides</label>
            <input type="search" id="q" placeholder="Search FAQs and self-service guides…"
                   value={query} onChange={event => setQuery(event.target.value)} />
            <button type="submit">Search</button>
          </form>

          {searchNote && <Notice style={{ marginTop: 14, maxWidth: 820 }}>{searchNote}</Notice>}
        </div>
      </section>

      {/* ============ Three routes ============ */}
      <section ref={routesRef} className={'routes reveal' + (routesShown ? ' is-visible' : '')}>
        {ROUTES.map(route => (
          <article className="route" key={route.num}>
            <span className="route__num">{route.num}</span>
            <RouteIcon num={route.num} />

            <h2>{route.title}</h2>
            <p>{route.body}</p>

            <div className="route__action">
              {route.to.startsWith('#')
                ? <a className="btn btn--ghost" href={route.to}>{route.action}</a>
                : <Link className="btn btn--ghost"
                        to={signedIn && route.signedInTo ? route.signedInTo : route.to}>
                    {route.action}
                  </Link>}
              {route.gated && !signedIn && <span className="route__gate">Requires an account</span>}
            </div>
          </article>
        ))}
      </section>

      {/* ============ How it works ============ */}
      <section ref={howtoRef} id="how-it-works"
               className={'howto reveal' + (howtoShown ? ' is-visible' : '')}>
        <Walkthrough />
      </section>

      {/* ============ Popular topics ============ */}
      <section ref={topicsRef} id="topics"
               className={'topics reveal' + (topicsShown ? ' is-visible' : '')}>
        <p className="topics__label">Popular help topics</p>
        <div className="chips">
          {TOPICS.map(topic => (
            <button className="chip" type="button" key={topic}
                    onClick={() => { setQuery(topic); runSearch(topic) }}>
              {topic}
            </button>
          ))}
        </div>
      </section>

      {/* ============ Call to action ============
          US-06: an unregistered student is told plainly that an account is what
          stands between them and a ticket. Someone already signed in is not. */}
      <section ref={ctaRef} className={'cta reveal' + (ctaShown ? ' is-visible' : '')}>
        <div className="cta__inner">
          {signedIn ? (
            <>
              <h2>Welcome back{firstNameOf(student) ? ', ' + firstNameOf(student) : ''}.
                  Pick up where you left off.</h2>
              <Link className="btn" to="/profile">Go to my profile</Link>
            </>
          ) : (
            <>
              <h2>Still stuck? Register and your ticket reaches the right desk in seconds.</h2>
              <Link className="btn" to="/register">Create an account</Link>
            </>
          )}
        </div>
      </section>

      {/* ============ Footer ============ */}
      <footer className="site-foot">
        <div className="site-foot__inner">
          {DESKS.map(desk => (
            <div className="desk" key={desk.name}>
              <strong>{desk.name}</strong>
              <a href={'mailto:' + desk.email}>{desk.email}</a>
              <span>{desk.phone}</span>
            </div>
          ))}

          <div className="foot-links">
            <a href="#">Accessibility</a>
            <a href="#">Privacy policy</a>
          </div>
        </div>

        <p className="site-foot__legal">SE2030 · Web-Based Help Desk System · Group MLBB8G204</p>
      </footer>
    </>
  )
}

function firstNameOf(student) {
  return (student.fullName || '').split(' ')[0] || ''
}

/* --------------------------------------------------------------------------
   The walkthrough player

   Chapter buttons seek the video and mark themselves current; the video keeps
   the list in step as it plays, so scrubbing the native controls updates the
   chapters too.
   -------------------------------------------------------------------------- */
function Walkthrough() {
  const videoRef = useRef(null)
  const [current, setCurrent] = useState(null)
  const [missing, setMissing] = useState(false)

  useEffect(() => {
    const video = videoRef.current
    if (!video) return

    function onTimeUpdate() {
      let index = null
      CHAPTERS.forEach((chapter, i) => {
        if (video.currentTime >= chapter.at) index = i
      })
      setCurrent(index)
    }

    function onError() { setMissing(true) }

    video.addEventListener('timeupdate', onTimeUpdate)
    video.addEventListener('error', onError, true)
    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate)
      video.removeEventListener('error', onError, true)
    }
  }, [])

  function seek(seconds) {
    const video = videoRef.current
    if (!video) return
    video.currentTime = seconds
    // Autoplay policies can refuse this; the poster simply stays put.
    video.play().catch(() => {})
  }

  return (
    <div className="howto__inner">
      <div className="howto__text">
        <p className="eyebrow">See how it works</p>
        <h2>Two minutes, start to finish.</h2>
        <p className="howto__lede">
          Watch one request travel the whole way — from a student typing it out,
          to the department picking it up, to the resolution landing back in
          their thread.
        </p>

        <ol className="chapters">
          {CHAPTERS.map((chapter, index) => (
            <li key={chapter.at}>
              <button type="button"
                      aria-current={current === index}
                      onClick={() => seek(chapter.at)}>
                <span className="chapters__t mono">{chapter.stamp}</span>
                {chapter.label}
              </button>
            </li>
          ))}
        </ol>
      </div>

      <div className="howto__player">
        {/* No file on the server yet: show where to put it, rather than a black
            rectangle with a broken control bar. */}
        {missing ? (
          <div className="howto__missing">
            <strong>The walkthrough is not uploaded yet.</strong>
            <p>Drop an MP4 here and this player picks it up:</p>
            <code>src/main/resources/static/media/how-it-works.mp4</code>
          </div>
        ) : (
          <video ref={videoRef} controls preload="metadata" poster={POSTER}>
            <source src="media/how-it-works.mp4" type="video/mp4" />
            Your browser cannot play this video.
          </video>
        )}
      </div>
    </div>
  )
}

/* The three outline icons. Inline rather than an icon font: three shapes do
   not justify a download, and these inherit the text colour for free. */
function RouteIcon({ num }) {
  const props = {
    width: 26, height: 26, viewBox: '0 0 24 24', fill: 'none',
    stroke: 'currentColor', strokeWidth: 1.6,
    strokeLinecap: 'round', strokeLinejoin: 'round'
  }

  if (num === '01') return (
    <svg {...props}>
      <path d="M2 4.5h7a3 3 0 0 1 3 3V20a2.5 2.5 0 0 0-2.5-2.5H2z" />
      <path d="M22 4.5h-7a3 3 0 0 0-3 3V20a2.5 2.5 0 0 1 2.5-2.5H22z" />
    </svg>
  )

  if (num === '02') return (
    <svg {...props}>
      <path d="M14 2.5H7a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7.5z" />
      <path d="M14 2.5v5h5" />
      <path d="M12 11.5v6M9 14.5h6" />
    </svg>
  )

  return (
    <svg {...props}>
      <path d="M3 6.5l2 2 3-3.5" />
      <path d="M3 13l2 2 3-3.5" />
      <path d="M3 19.5l2 2 3-3.5" />
      <path d="M12 7h9M12 13.5h9M12 20h9" />
    </svg>
  )
}
