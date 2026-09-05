// Colour + dot + label tag. Exact per-status colours from the "My Tickets"
// handoff's Design Tokens -> Status colors section (this is the
// authoritative spec; Ticket Detail reuses the same component/colours).
const STATUS_META = {
  OPEN: {
    label: "OPEN",
    badgeBg: "var(--color-accent-100)",
    badgeFg: "var(--color-accent-800)",
    dot: "var(--color-accent)",
  },
  IN_PROGRESS: {
    label: "IN PROGRESS",
    badgeBg: "var(--color-neutral-200)",
    badgeFg: "var(--color-neutral-900)",
    dot: "var(--color-neutral-600)",
  },
  RESOLVED: {
    // Inverted, solid-dark pill per spec.
    label: "RESOLVED",
    badgeBg: "var(--color-text)",
    badgeFg: "var(--color-bg)",
    dot: "var(--color-bg)",
  },
  // Bookmarks handoff -> "Articles tab": neutral-200 bg / neutral-900 text,
  // reusing the IN_PROGRESS look since no separate dot colour is specified.
  ARTICLE: {
    label: "ARTICLE",
    badgeBg: "var(--color-neutral-200)",
    badgeFg: "var(--color-neutral-900)",
    dot: "var(--color-neutral-600)",
  },
};

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] ?? {
    label: status,
    badgeBg: "var(--color-neutral-100)",
    badgeFg: "var(--color-neutral-800)",
    dot: "var(--color-neutral-500)",
  };
  return (
    <span className="tag" style={{ background: meta.badgeBg, color: meta.badgeFg, gap: 7 }}>
      <span
        style={{
          width: 7,
          height: 7,
          background: meta.dot,
          display: "inline-block",
        }}
      />
      {meta.label}
    </span>
  );
}
