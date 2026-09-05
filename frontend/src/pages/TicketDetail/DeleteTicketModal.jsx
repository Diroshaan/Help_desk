import { AlertTriangle } from "lucide-react";
import Dialog from "../../components/Dialog.jsx";

export default function DeleteTicketModal({ ticket, onClose, onConfirm }) {
  return (
    <Dialog onClose={onClose} style={{ border: "2px solid var(--color-accent)" }}>
      <span
        style={{
          width: 36,
          height: 36,
          background: "var(--color-accent)",
          color: "var(--color-bg)",
          display: "grid",
          placeItems: "center",
        }}
      >
        <AlertTriangle size={18} />
      </span>
      <div className="dialog-title">
        Delete ticket <span style={{ fontFamily: "var(--font-mono)", fontSize: 18 }}>{ticket.id}</span>?
      </div>
      <div className="dialog-body">
        This is permanent. {ticket.department} will no longer see the request, the conversation
        and its attachments are removed, and nothing can be recovered.
      </div>
      <div className="dialog-actions">
        <button className="btn btn-secondary" onClick={onClose}>
          Cancel
        </button>
        <button className="btn btn-primary" onClick={onConfirm}>
          Delete ticket
        </button>
      </div>
    </Dialog>
  );
}
