import { useEffect, useRef, useState } from "react";
import { MoreVertical } from "lucide-react";

/**
 * Per-card action menu (handoff: "Kebab menu on each card opens a
 * card-specific menu - e.g. remove bookmark, move to folder, not itemized
 * beyond the drag-and-drop note"). Both items are placeholders - no real
 * bookmark-removal or refiling logic is specified in the mock yet.
 */
export default function CardKebabMenu() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    function handleClick(e) {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  return (
    <div ref={rootRef} style={{ position: "relative" }} onClick={(e) => e.stopPropagation()}>
      <button
        className="btn btn-icon"
        style={{ width: 24, height: 24 }}
        aria-label="Bookmark actions"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <MoreVertical size={14} />
      </button>
      {open && (
        <div
          role="menu"
          style={{
            position: "absolute",
            right: 0,
            top: "calc(100% + 4px)",
            background: "var(--color-surface)",
            border: "1px solid var(--color-divider)",
            minWidth: 160,
            zIndex: 10,
            boxShadow: "var(--shadow-md)",
          }}
        >
          <button role="menuitem" className="btn btn-block" style={{ margin: 0 }} onClick={() => setOpen(false)}>
            Remove bookmark
          </button>
          <button role="menuitem" className="btn btn-block" style={{ margin: 0 }} onClick={() => setOpen(false)}>
            Move to folder
          </button>
        </div>
      )}
    </div>
  );
}
