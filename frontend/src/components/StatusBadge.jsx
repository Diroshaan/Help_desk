// Colour + dot + label tag, matching the handoff's OPEN tag exactly and
// extending the same "single accent, dot means status" pattern to the two
// statuses the handoff didn't need to show. The design system commits to a
// single accent colour, so IN_PROGRESS and RESOLVED are differentiated with
// neutral tones + the dot rather than a second accent hue.
const STATUS_META = {
  OPEN: { label: "OPEN", tagClass: "tag-accent", dot: "var(--color-accent)" },
  IN_PROGRESS: {
    label: "IN PROGRESS",
    tagClass: "tag-neutral",
    dot: "var(--color-neutral-600)",
  },
  RESOLVED: {
    label: "RESOLVED",
    tagClass: "tag-neutral",
    dot: "var(--color-text)",
  },
};

export default function StatusBadge({ status }) {
  const meta = STATUS_META[status] ?? {
    label: status,
    tagClass: "tag-neutral",
    dot: "var(--color-neutral-500)",
  };
  return (
    <span className={`tag ${meta.tagClass}`} style={{ gap: 7 }}>
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
