import { useState } from "react";
import { Bookmark, Pencil, Trash2 } from "lucide-react";
import BackLink from "../../components/BackLink.jsx";
import StatusBadge from "../../components/StatusBadge.jsx";
import PriorityTag from "../../components/PriorityTag.jsx";
import AttachmentChip from "./AttachmentChip.jsx";
import StarRating from "./StarRating.jsx";
import EditTicketModal from "./EditTicketModal.jsx";
import DeleteTicketModal from "./DeleteTicketModal.jsx";
import { mockTicket } from "../../data/mockTicket.js";
import "./TicketDetail.css";

const TIMELINE_DOT = {
  done: { fill: "var(--color-accent)", ring: "var(--color-accent)", line: "var(--color-accent)", anim: "none", op: 1 },
  current: {
    fill: "var(--color-accent)",
    ring: "var(--color-accent)",
    line: "var(--color-neutral-300)",
    anim: "pulse 2s ease-in-out infinite",
    op: 1,
  },
  upcoming: { fill: "transparent", ring: "var(--color-neutral-400)", line: "transparent", anim: "none", op: 0.5 },
};

function MessageCard({ name, initials, timestamp, body, attachments, officer = false }) {
  return (
    <div
      style={{
        border: officer ? "1px solid var(--color-accent-200)" : "1px solid var(--color-divider)",
        background: officer ? "var(--color-accent-100)" : "transparent",
        padding: 18,
        marginBottom: 18,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: attachments ? 12 : 0 }}>
        <span
          style={{
            width: 28,
            height: 28,
            background: officer ? "var(--color-accent)" : "var(--color-text)",
            color: officer ? "var(--color-on-accent)" : "var(--color-bg)",
            display: "grid",
            placeItems: "center",
            fontSize: 11,
          }}
        >
          {initials}
        </span>
        <strong style={{ fontSize: 14 }}>{name}</strong>
        {officer ? (
          <span className="tag" style={{ background: "var(--color-accent)", color: "var(--color-on-accent)" }}>
            Help Desk Officer
          </span>
        ) : (
          <span className="tag tag-neutral">You</span>
        )}
        <span style={{ marginLeft: "auto", fontSize: 12, opacity: officer ? 0.55 : 0.5 }}>{timestamp}</span>
      </div>
      <p style={{ fontSize: 14, margin: attachments ? "0 0 14px" : 0, opacity: officer ? 0.9 : 0.85 }}>{body}</p>
      {attachments && attachments.length > 0 && (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {attachments.map((a) => (
            <AttachmentChip key={a.id} attachment={a} />
          ))}
        </div>
      )}
    </div>
  );
}

function ResolutionCard({ resolution }) {
  if (!resolution) {
    return (
      <div
        style={{
          border: "1px solid var(--color-divider)",
          borderLeft: "3px solid var(--color-text)",
          padding: 18,
          marginBottom: 18,
          opacity: 0.55,
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
          <span style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>
            Resolution — appears once published
          </span>
        </div>
        <p style={{ fontSize: 13, margin: 0 }}>
          An official resolution carries this left rule and sets the ticket to RESOLVED,
          unlocking the feedback card.
        </p>
      </div>
    );
  }
  return (
    <div
      style={{
        border: "1px solid var(--color-divider)",
        borderLeft: "3px solid var(--color-text)",
        padding: 18,
        marginBottom: 18,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
        <span style={{ fontSize: 11, letterSpacing: "0.1em", textTransform: "uppercase" }}>
          Resolution
        </span>
        <span style={{ marginLeft: "auto", fontSize: 12, opacity: 0.5 }}>{resolution.timestamp}</span>
      </div>
      <p style={{ fontSize: 13, margin: 0 }}>{resolution.body}</p>
    </div>
  );
}

function FeedbackCard({ ticket }) {
  const isResolved = ticket.status === "RESOLVED";
  const [submitted, setSubmitted] = useState(ticket.feedback ?? null);
  const [rating, setRating] = useState(ticket.feedback?.rating ?? 4);
  const [comment, setComment] = useState(ticket.feedback?.comment ?? "");
  const [editing, setEditing] = useState(false);

  const showForm = isResolved && (!submitted || editing);

  return (
    <div style={{ border: "1px solid var(--color-divider)", padding: 18 }}>
      <h6 style={{ marginBottom: 6 }}>Feedback</h6>
      {!isResolved && (
        <p style={{ fontSize: 12, opacity: 0.6, marginBottom: 12 }}>
          Shown once the ticket is RESOLVED. Preview:
        </p>
      )}

      {showForm && (
        <>
          <StarRating value={rating} onChange={setRating} />
          <textarea
            className="input"
            rows={3}
            placeholder="What worked, what didn't?"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
          />
          <button
            className="btn btn-primary btn-block"
            style={{ justifyContent: "flex-start", paddingLeft: 14 }}
            onClick={() => {
              setSubmitted({ rating, comment });
              setEditing(false);
            }}
          >
            Submit feedback
          </button>
        </>
      )}

      {isResolved && submitted && !editing && (
        <>
          <StarRating value={submitted.rating} readOnly />
          <p style={{ fontSize: 13, margin: "0 0 10px" }}>{submitted.comment}</p>
          <a href="#" className="btn-ghost btn" style={{ padding: 0 }} onClick={(e) => { e.preventDefault(); setEditing(true); }}>
            Edit feedback
          </a>
        </>
      )}

      {!isResolved && (
        <>
          <StarRating value={rating} readOnly />
          <textarea className="input" rows={3} placeholder="What worked, what didn't?" disabled />
          <button className="btn btn-primary btn-block" style={{ justifyContent: "flex-start", paddingLeft: 14 }} disabled>
            Submit feedback
          </button>
        </>
      )}
    </div>
  );
}

export default function TicketDetail({ ticket = mockTicket, onBack }) {
  const [isBookmarked, setIsBookmarked] = useState(ticket.isBookmarked);
  const [modal, setModal] = useState(null); // null | "edit" | "delete"
  const isOpen = ticket.status === "OPEN";

  const details = [
    { k: "Status", v: ticket.status },
    { k: "Department", v: ticket.department },
    { k: "Priority", v: ticket.priority },
    { k: "Assigned officer", v: ticket.officer ? ticket.officer.name : "Unassigned" },
    { k: "Created", v: ticket.request.timestamp },
    { k: "Last updated", v: ticket.updatedAt ? new Date(ticket.updatedAt).toLocaleString(undefined, { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "—" },
    { k: "Attachments", v: `${ticket.request.attachments.length} files` },
  ];

  return (
    <div>
      <div style={{ padding: "28px 32px 24px", borderBottom: "2px solid var(--color-divider)", background: "var(--color-surface)" }}>
        <BackLink onClick={onBack}>My tickets</BackLink>
        <div style={{ display: "flex", alignItems: "flex-start", gap: 20, marginTop: 16 }}>
          <div style={{ flex: 1, borderLeft: "3px solid var(--color-accent)", paddingLeft: 18 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 14, marginBottom: 10 }}>
              <span style={{ fontFamily: "var(--font-mono)", fontSize: 14, opacity: 0.6 }}>{ticket.id}</span>
              <StatusBadge status={ticket.status} />
              <PriorityTag priority={ticket.priority} />
            </div>
            <h2 style={{ margin: 0, maxWidth: "34ch", fontSize: 26 }}>{ticket.subject}</h2>
          </div>
          <div style={{ display: "flex", gap: 8, flex: "none" }}>
            <button
              className="btn btn-icon btn-secondary"
              aria-label={isBookmarked ? "Remove bookmark" : "Bookmark ticket"}
              aria-pressed={isBookmarked}
              onClick={() => setIsBookmarked((v) => !v)}
            >
              <Bookmark size={16} fill={isBookmarked ? "currentColor" : "none"} />
            </button>
            {isOpen && (
              <>
                <button className="btn btn-secondary" onClick={() => setModal("edit")}>
                  <Pencil size={15} />
                  Edit
                </button>
                <button
                  className="btn btn-secondary"
                  style={{ color: "var(--color-accent-700)", borderColor: "var(--color-accent)" }}
                  onClick={() => setModal("delete")}
                >
                  <Trash2 size={15} />
                  Delete
                </button>
              </>
            )}
          </div>
        </div>
      </div>

      <div className="ticket-detail__body">
        <div style={{ borderRight: "2px solid var(--color-divider)", padding: "26px 32px" }}>
          <h6 style={{ marginBottom: 18 }}>Conversation</h6>

          <MessageCard
            name={ticket.request.name}
            initials={ticket.request.initials}
            timestamp={ticket.request.timestamp}
            body={ticket.request.body}
            attachments={ticket.request.attachments}
          />

          {ticket.officerUpdates.map((u) => (
            <MessageCard key={u.id} name={u.name} initials={u.initials} timestamp={u.timestamp} body={u.body} officer />
          ))}

          <ResolutionCard resolution={ticket.resolution} />
        </div>

        <div style={{ padding: "26px 28px", display: "flex", flexDirection: "column", gap: 26 }}>
          <div>
            <h6 style={{ marginBottom: 14 }}>Details</h6>
            <div style={{ display: "flex", flexDirection: "column", fontSize: 13 }}>
              {details.map((d) => (
                <div
                  key={d.k}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    gap: 16,
                    padding: "9px 0",
                    borderBottom: "1px solid var(--color-divider)",
                  }}
                >
                  <span style={{ opacity: 0.55 }}>{d.k}</span>
                  <span style={{ textAlign: "right", fontWeight: 600 }}>{d.v}</span>
                </div>
              ))}
            </div>
          </div>

          <div>
            <h6 style={{ marginBottom: 16 }}>Status timeline</h6>
            <div style={{ display: "flex", flexDirection: "column" }}>
              {ticket.timeline.map((s) => {
                const dot = TIMELINE_DOT[s.state];
                return (
                  <div key={s.key} style={{ display: "flex", gap: 14 }}>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", flex: "none" }}>
                      <span
                        style={{
                          width: 11,
                          height: 11,
                          background: dot.fill,
                          border: `2px solid ${dot.ring}`,
                          animation: dot.anim,
                        }}
                      />
                      <span style={{ width: 2, flex: 1, background: dot.line, minHeight: 34 }} />
                    </div>
                    <div style={{ paddingBottom: 14 }}>
                      <div style={{ fontSize: 13, fontWeight: 600, opacity: dot.op }}>{s.label}</div>
                      <div style={{ fontSize: 11, opacity: 0.5, marginTop: 2 }}>{s.meta}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <FeedbackCard ticket={ticket} />
        </div>
      </div>

      {modal === "edit" && <EditTicketModal ticket={ticket} onClose={() => setModal(null)} />}
      {modal === "delete" && (
        <DeleteTicketModal ticket={ticket} onClose={() => setModal(null)} onConfirm={() => setModal(null)} />
      )}
    </div>
  );
}
