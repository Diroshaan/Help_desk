import { useEffect, useRef, useState } from "react";
import { MoreVertical } from "lucide-react";

/**
 * Row action menu (handoff: "Opens a menu offering Edit and Delete - only
 * enabled while the ticket's status is OPEN"). The kebab button itself is
 * disabled outside OPEN so no menu can be opened at all.
 */
export default function KebabMenu({ ticket, onEdit, onDelete }) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef(null);
  const isOpen = ticket.status === "OPEN";

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
        style={{ width: 28, height: 28 }}
        disabled={!isOpen}
        aria-label="Ticket actions"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
      >
        <MoreVertical size={15} />
      </button>
      {open && isOpen && (
        <div
          role="menu"
          style={{
            position: "absolute",
            right: 0,
            top: "calc(100% + 4px)",
            background: "var(--color-surface)",
            border: "1px solid var(--color-divider)",
            minWidth: 140,
            zIndex: 10,
            boxShadow: "var(--shadow-md)",
          }}
        >
          <button
            role="menuitem"
            className="btn btn-block"
            style={{ margin: 0, justifyContent: "flex-start" }}
            onClick={() => {
              setOpen(false);
              onEdit();
            }}
          >
            Edit
          </button>
          <button
            role="menuitem"
            className="btn btn-block"
            style={{ margin: 0, justifyContent: "flex-start", color: "var(--color-accent-700)" }}
            onClick={() => {
              setOpen(false);
              onDelete();
            }}
          >
            Delete
          </button>
        </div>
      )}
    </div>
  );
}
