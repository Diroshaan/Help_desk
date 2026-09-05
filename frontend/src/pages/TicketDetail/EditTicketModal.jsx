import { X, Image as ImageIcon } from "lucide-react";
import Dialog from "../../components/Dialog.jsx";

export default function EditTicketModal({ ticket, onClose }) {
  return (
    <Dialog
      onClose={onClose}
      style={{ width: "min(640px, 100%)", border: "2px solid var(--color-text)" }}
    >
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <div className="dialog-title">
          Edit ticket{" "}
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 16 }}>{ticket.id}</span>
        </div>
        <button className="btn btn-icon" onClick={onClose} aria-label="Close">
          <X size={16} />
        </button>
      </div>

      <div
        style={{
          borderLeft: "3px solid var(--color-accent)",
          background: "var(--color-accent-100)",
          padding: "10px 12px",
          fontSize: 13,
        }}
      >
        You can only edit tickets while they are OPEN.
      </div>

      <div className="field">
        <label>Subject</label>
        <input className="input" defaultValue={ticket.subject} onChange={() => {}} />
      </div>

      <div className="field">
        <label>Department category</label>
        <select className="input" defaultValue={ticket.department} onChange={() => {}}>
          <option>IT Services</option>
          <option>Registration</option>
        </select>
      </div>

      <div className="field">
        <label>Description</label>
        <textarea className="input" rows={4} defaultValue={ticket.request.body} onChange={() => {}} />
      </div>

      <div className="field">
        <label>Attachments</label>
        {ticket.request.attachments.map((a) => (
          <div
            key={a.id}
            style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              padding: "8px 0",
              borderBottom: "1px solid var(--color-divider)",
              fontSize: 13,
            }}
          >
            <ImageIcon size={15} style={{ opacity: 0.6 }} />
            <span style={{ flex: 1 }}>{a.name}</span>
            <a href="#" onClick={(e) => e.preventDefault()} style={{ color: "inherit", textDecoration: "none", opacity: 0.5 }}>
              ✕
            </a>
          </div>
        ))}
        <div
          style={{
            border: "1px dashed var(--color-divider)",
            padding: 14,
            marginTop: 10,
            fontSize: 13,
            opacity: 0.7,
          }}
        >
          Drag files here to append, or <a href="#" onClick={(e) => e.preventDefault()}>browse</a>
        </div>
      </div>

      <div className="dialog-actions">
        <button className="btn btn-secondary" onClick={onClose}>
          Cancel
        </button>
        <button className="btn btn-primary" disabled>
          Save changes
        </button>
      </div>
      <div style={{ fontSize: 11, opacity: 0.5 }}>
        Saving adds an "Edited by student" entry to the timeline. Save stays disabled until a
        field actually changes.
      </div>
    </Dialog>
  );
}
