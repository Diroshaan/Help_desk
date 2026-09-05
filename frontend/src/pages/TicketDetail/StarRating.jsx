export default function StarRating({ value, onChange, readOnly = false, max = 5 }) {
  return (
    <div
      style={{ display: "flex", gap: 6, marginBottom: 12 }}
      role="radiogroup"
      aria-label="Rating out of 5 stars"
    >
      {Array.from({ length: max }, (_, i) => i + 1).map((n) => (
        <a
          key={n}
          href="#"
          role="radio"
          aria-checked={n === value}
          aria-label={`${n} star${n > 1 ? "s" : ""}`}
          onClick={(e) => {
            e.preventDefault();
            if (!readOnly) onChange?.(n);
          }}
          style={{
            fontSize: 26,
            lineHeight: 1,
            textDecoration: "none",
            color: n <= value ? "var(--color-accent)" : "var(--color-neutral-400)",
            cursor: readOnly ? "default" : "pointer",
          }}
        >
          {n <= value ? "★" : "☆"}
        </a>
      ))}
    </div>
  );
}
