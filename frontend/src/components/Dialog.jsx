import { useEffect, useRef } from "react";
import "./Dialog.css";

/**
 * Shared dialog shell (handoff: .dialog-backdrop / .dialog). Individual
 * modals (EditTicketModal, DeleteTicketModal, ...) pass their own border
 * colour and content as children.
 */
export default function Dialog({ children, onClose, style }) {
  const dialogRef = useRef(null);

  useEffect(() => {
    dialogRef.current?.focus();
    function handleKey(e) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onClose]);

  return (
    <div className="dialog-backdrop" role="presentation" onClick={onClose}>
      <div
        className="dialog"
        style={style}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        {children}
      </div>
    </div>
  );
}
