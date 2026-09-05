import { useEffect, useRef, useState } from "react";
import "./ConfirmModal.css";

/**
 * Small danger confirmation modal (spec 2.6 - Delete Ticket Confirmation).
 * The confirm button stays disabled for a short beat after the modal
 * finishes rendering, so an accidental double-click can't fire it.
 */
export default function ConfirmModal({
  title,
  body,
  confirmLabel = "Confirm",
  onConfirm,
  onCancel,
}) {
  const [armed, setArmed] = useState(false);
  const dialogRef = useRef(null);

  useEffect(() => {
    const armTimer = setTimeout(() => setArmed(true), 350);
    dialogRef.current?.focus();

    function handleKey(e) {
      if (e.key === "Escape") onCancel();
    }
    document.addEventListener("keydown", handleKey);
    return () => {
      clearTimeout(armTimer);
      document.removeEventListener("keydown", handleKey);
    };
  }, [onCancel]);

  return (
    <div className="confirm-modal__overlay" role="presentation" onClick={onCancel}>
      <div
        className="confirm-modal"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        tabIndex={-1}
        ref={dialogRef}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="confirm-modal__icon" aria-hidden="true">!</div>
        <h2 id="confirm-modal-title" className="confirm-modal__title">
          {title}
        </h2>
        <p className="confirm-modal__body">{body}</p>
        <div className="confirm-modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn--danger"
            disabled={!armed}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
